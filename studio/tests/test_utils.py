import io
import os
import tempfile
import shutil
import zipfile
import json
import tarfile

def generate(name, content):
  if name.endswith(".zip") or name.endswith(".jar"):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as output_zip:
      for f, c in content.items():
        data = generate(f, c)
        output_zip.writestr(f, data.getvalue())
    return buffer
  elif name.endswith(".tar.gz"):
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
      for f, c in content.items():
        data = generate(f, c).getvalue()
        tarinfo = tarfile.TarInfo(f)
        tarinfo.size = len(data)
        tar.addfile(tarinfo, io.BytesIO(data))
    return buffer
  elif name.endswith(".json"):
    data = json.JSONEncoder().encode(content).encode("utf-8")
    return io.BytesIO(data)
  else:
    data = content.encode('utf-8')
    return io.BytesIO(data)

def create(name, content):
    data = generate(name, content)
    with open(name, "wb") as f:
        f.write(data.getvalue())

def create_all(dir, contents):
    for name, content in contents.items():
        file_name = os.path.join(dir, name)
        base_dir = os.path.dirname(file_name)
        os.makedirs(base_dir, exist_ok=True)
        create(file_name, content)

def readstr(path):
  with open(path, 'r') as file:
    return file.read()

def deploy_py(env_var):
  deploy_files = os.environ[env_var].split(" ")
  deploy_dir = tempfile.mkdtemp()
  for file in deploy_files:
    rel = os.path.dirname(file)
    os.makedirs(os.path.join(deploy_dir, rel), exist_ok=True)
    shutil.copy(file, os.path.join(deploy_dir, file))
  return deploy_dir
