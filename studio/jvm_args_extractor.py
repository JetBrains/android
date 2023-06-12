from pathlib import Path
import argparse
import json
import platform
import sys


# This script extracts the JVM args used in AS release builds
# by inspecting product-info.json in the IDE distribution.
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("product_info", type=Path)
    parser.add_argument("out", type=Path)
    args = parser.parse_args()

    product_info_file: Path = args.product_info
    out: Path = args.out

    # Read JVM args from product-info.json in the IDE distribution.
    product_info = json.loads(product_info_file.read_text("ascii"))
    (launch_config,) = product_info["launch"]
    required_jvm_args = launch_config["additionalJvmArguments"]

    # Get ready to substitute $IDE_HOME path vars.
    ide_home = product_info_file.parent
    if platform.system() == "Darwin":
        ide_home = ide_home.parent  # Handle the extra "Resources" directory on Mac.
    ide_home_macro = get_ide_home_macro()

    # Write the argfile.
    with open(out, "w", encoding="ascii") as f:
        f.write("# This file is generated based on product-info.json in IntelliJ\n")
        for arg in required_jvm_args:
            arg = arg.replace(ide_home_macro, str(ide_home))
            f.write(f"{arg}\n")


def get_ide_home_macro() -> str:
    system = platform.system()
    if system == "Linux": return "$IDE_HOME"
    elif system == "Darwin": return "$APP_PACKAGE/Contents"
    elif system == "Windows": return "%IDE_HOME%"
    else: sys.exit(f"Unrecognized system: {system}")


if __name__ == "__main__":
    main()
