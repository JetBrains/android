#!/usr/bin/env python3

from pathlib import Path
import argparse
import glob
import itertools
import math
import networkx as nx
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def main():
  """Builds an IML module dependency graph, simplified via transitive reduction."""
  parser = argparse.ArgumentParser(description='Build an IML module dependency graph, simplified via transitive reduction.')
  parser.add_argument('modules_xml', metavar='/path/to/.idea/modules.xml', type=Path)
  parser.add_argument('--out', metavar='FILE', type=Path, help='output file')
  parser.add_argument('--format', metavar='EXT', help='file type; usually inferred from output file name')
  parser.add_argument('--transitive-reduction', action='store_true', help='remove edges that are already implied by transitivity')
  parser.add_argument('--num-modules', metavar='N', type=int, help='only show the largest N modules')
  parser.add_argument('--count-lines', action='store_true', help='quantify module size by lines instead of files')
  parser.add_argument('--draw-to-scale', action='store_true', help='draw nodes proportional to module size')
  parser.add_argument('--include-tests', action='store_true', help='include test sources and test-scoped dependencies')
  parser.add_argument('--exclude', metavar='GLOB', action='append', help='hide modules matching GLOB')
  parser.add_argument('--full-module-names', action='store_true', help='use fully qualified module names')
  parser.add_argument('--print-module-sizes', action='store_true', help='print the size of the rendered modules at the end')
  args = parser.parse_args()

  # Build the dependency graph.
  iml_files = collect_iml_files(args.modules_xml)
  iml_modules = [ImlModule(iml_file, args) for iml_file in iml_files]
  g = build_module_graph(iml_modules, args)
  print(f'Full graph: {g.number_of_nodes()} nodes, {g.number_of_edges()} edges.')

  # Prune and simplify.
  break_cycles(g)  # Required for transitive reduction.
  prune_graph(g, args)
  print(f'After pruning: {g.number_of_nodes()} nodes, {g.number_of_edges()} edges.')
  if args.transitive_reduction:
    tr = nx.transitive_reduction(g)
    tr.add_nodes_from(g.nodes(data=True))  # Restore node attributes.
    tr.add_edges_from((u, v, g.edges[u, v]) for u, v in tr.edges)  # Restore edge attributes.
    g = tr
  print(f'After transitive reduction: {g.number_of_edges()} edges.')

  # Stylize and render.
  if not g:
    sys.exit('Graph is empty.')
  stylize_graph(g, args)
  for node, attrs in g.nodes(data=True):
    del attrs['iml_module']
  dot_graph = nx.drawing.nx_pydot.to_pydot(g)
  ext = args.format or (args.out.suffix[1:] if args.out else 'svg')
  dot_format = 'raw' if ext == 'dot' else ext
  out_file = args.out or Path(f'/tmp/iml-module-graph.{ext}')
  print(f'Rendering to file: {out_file}')
  dot_graph.write(out_file, format=dot_format)
  if args.print_module_sizes:
    print() # empty line to make it easier to read
    print_module_sizes(args.count_lines, iml_modules, g)

def print_module_sizes(count_lines, iml_modules, g):
  print('Module sizes:')
  for module in iml_modules:
    if module.name in g:
      print(f'{module.display_name} = {module.size} {"lines of code" if count_lines else "files"}')


def collect_iml_files(modules_xml):
  modules_xml_root = ET.parse(modules_xml, ET.XMLParser(encoding='UTF-8')).getroot()
  modules = modules_xml_root.findall("./component[@name='ProjectModuleManager']/modules/module")
  project_dir = str(modules_xml.parent.parent)

  iml_files = []
  for module in modules:
    module_path = module.get('filepath').replace('$PROJECT_DIR$', project_dir)
    iml_files.append(Path(module_path).resolve())

  for f in iml_files:
    if not f.exists():
      print('Ignoring missing module file:', f.name)
  iml_files = [f for f in iml_files if f.exists()]

  return sorted(set(iml_files))


class ImlModule:
  def __init__(self, iml_file, args):
    self.iml_file = iml_file
    self.name = iml_file.name[:-len('.iml')]
    self.short_name = remove_prefixes(self.name, ['intellij.android.', 'android.sdktools.', 'intellij.'])
    self.display_name = self.name if args.full_module_names else self.short_name
    self.xml_root = ET.parse(iml_file, ET.XMLParser(encoding='UTF-8')).getroot()
    self.size = compute_module_size(self.iml_file, self.xml_root, args)


def build_module_graph(iml_modules, args):
  """Builds the module graph and stores useful info as node attributes."""
  g = nx.DiGraph()
  for iml_module in iml_modules:
    g.add_node(iml_module.name, iml_module=iml_module)
  for iml_module in iml_modules:
    deps_xpath = "./component[@name='NewModuleRootManager']/orderEntry[@type='module']"
    order_entries = iml_module.xml_root.findall(deps_xpath)
    for order_entry in order_entries:
      dep_name = order_entry.get('module-name')
      scope = order_entry.get('scope', default='COMPILE')
      if dep_name not in g:
        print(f"Ignoring invalid dependency: {iml_module.name} -> {dep_name}")
        continue
      if scope == 'TEST' and not args.include_tests:
        continue
      attrs = {'style': 'dashed'} if scope == 'RUNTIME' else {}
      g.add_edge(iml_module.name, dep_name, **attrs)
  return g


def prune_graph(g, args):
  # Remove empty modules. This primarily removes test modules, which have
  # a size of 0 once test source roots are excluded.
  for node, iml_module in list(g.nodes(data='iml_module')):
    if iml_module.size == 0:
      remove_node(g, node)
  # Remove explicitly excluded modules.
  if args.exclude:
    exclude_patterns = [re.escape(glob).replace('\\*', '.*') for glob in args.exclude]
    any_exclude_pattern = re.compile('|'.join(exclude_patterns))
    for node, iml_module in list(g.nodes(data='iml_module')):
      if any_exclude_pattern.fullmatch(iml_module.name):
        remove_node(g, node)
  # Limit graph size by removing smaller nodes. This should be done last.
  if args.num_modules:
    large_modules = sorted(g, key=lambda m: g.nodes[m]['iml_module'].size, reverse=True)
    large_modules = set(large_modules[:args.num_modules])
    for node in list(g):
      if node not in large_modules:
        remove_node(g, node)


def remove_node(g, node):
  """Remove a node, preserving transitive dependency paths."""
  transitive_paths = itertools.product(g.predecessors(node), g.successors(node))
  g.remove_node(node)
  for (u, v) in transitive_paths:
    if not nx.has_path(g, u, v):
      g.add_edge(u, v, style='dotted')


def break_cycles(g):
  # Cycles are rare, but do happen in e.g. the IntelliJ codebase.
  while True:
    try:
      cycle = nx.find_cycle(g)
    except nx.NetworkXNoCycle:
      break
    # For now we break the cycle by removing a random edge.
    (u, v) = cycle[0]
    print(f'Found a cycle of {len(cycle)} nodes. Removing edge: {u} -> {v}')
    g.remove_edge(u, v)


def stylize_graph(g, args):
  """Add Graphviz style attributes."""
  # See https://graphviz.org/doc/info/attrs.html for valid attributes.
  module_sizes = sorted([m.size for (_, m) in g.nodes(data='iml_module')])
  module_sizes_sum = sum(module_sizes)
  graph_defaults = g.graph['graph'] = {}
  node_defaults = g.graph['node'] = {}
  node_defaults['style'] = 'filled'
  if args.draw_to_scale:
    # To draw things to scale, we use circles with area proportional to module size.
    # Future work: maybe tune penwidth and arrowsize too.
    total_foreground_area = 5 * 8  # Square inches.
    node_defaults['shape'] = 'circle'
    node_defaults['fixedsize'] = True
    graph_defaults['ranksep'] = .25
    for node, attrs in g.nodes.items():
      iml_module = attrs['iml_module']
      node_area = (iml_module.size / module_sizes_sum) * total_foreground_area
      circle_diameter = math.sqrt(node_area)
      attrs['width'] = circle_diameter
      label = attrs['label'] = split_into_lines(iml_module.display_name)
      label_width = max(len(s) for s in label.split('\n'))
      label_height = 2 * label.count('\n') + 2
      label_diameter = math.hypot(label_width, label_height)
      attrs['fontsize'] = 120 * circle_diameter / label_diameter
  else:
    # Even when not drawing to scale, we still emphasize large modules.
    for node, attrs in g.nodes.items():
      iml_module = attrs['iml_module']
      size = iml_module.size
      large = module_sizes[len(module_sizes) * 9 // 10]
      medium = module_sizes[len(module_sizes) * 7 // 10]
      attrs['fontsize'] = 24 if size >= large else 15 if size >= medium else 11
      attrs['label'] = iml_module.display_name


def compute_module_size(iml_file, xml_root, args):
  sources_xpath = "./component[@name='NewModuleRootManager']/content/sourceFolder"
  source_folders = xml_root.findall(sources_xpath)
  module_dir = str(iml_file.parent)
  size = 0
  for src in source_folders:
    if src.get('generated') == 'true' or src.get('type') in ['java-resource', 'java-test-resource']:
      continue
    if src.get('isTestSource') == 'true' and not args.include_tests:
      continue
    src_dir = Path(src.get('url')[len('file://'):].replace('$MODULE_DIR$', module_dir))
    if not src_dir.exists():
      print('Ignoring missing source root:', src_dir)
      continue
    source_files = list(src_dir.rglob('*.java')) + list(src_dir.rglob('*.kt'))
    for source_file in source_files:
      size += count_lines(source_file) if args.count_lines else 1
  return size


def count_lines(file_path):
  num_lines = 0
  with open(file_path) as f:
    for line in f:
      line = line.strip()
      if line and not line.startswith('import '):
        num_lines += 1
  return num_lines


def remove_prefixes(s, prefixes):
  for prefix in prefixes:
    if s.startswith(prefix):
      s = s[len(prefix):]
  return s


def split_into_lines(s):
  """Split a string like "some.module.name" into multiple lines to reduce width."""
  s = re.sub(r'([A-Z])', '.\\1', s).lower()  # Split by camel case.
  s = '\n'.join(re.split('\\.|-|_', s))  # Split by common delimiters.
  return s


if __name__ == '__main__':
  main()
