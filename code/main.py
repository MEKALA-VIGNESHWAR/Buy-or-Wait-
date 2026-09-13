#!/usr/bin/env python3
import os
import subprocess
import sys

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    jar_path = os.path.join(root_dir, "target", "buy-or-wait.jar")

    # If JAR does not exist, build with maven
    if not os.path.exists(jar_path):
        print("Building Buy or Wait Spring Boot application with Maven...")
        mvn_cmd = "mvn.cmd" if os.name == "nt" else "mvn"
        subprocess.check_call([mvn_cmd, "package", "-DskipTests"], cwd=root_dir)

    print(f"Executing Buy or Wait application from {jar_path}...")
    proc = subprocess.run(["java", "-jar", jar_path], cwd=root_dir)
    sys.exit(proc.returncode)

if __name__ == "__main__":
    main()
