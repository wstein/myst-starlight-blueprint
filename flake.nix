{
  description = "MyST -> Starlight blueprint: JDK 21 + Node 22 + Gradle for the tool/myst/site pipeline";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          # Matches CI (.github/workflows/deploy.yml): temurin-21 + node-22 +
          # typst/qpdf for the PDF export (`make pdf`) — no LaTeX needed.
          packages = [
            pkgs.jdk21
            pkgs.nodejs_22
            pkgs.gradle
            pkgs.typst
            pkgs.qpdf
          ];

          JAVA_HOME = pkgs.jdk21.home;

          shellHook = ''
            if ! command -v myst >/dev/null 2>&1; then
              echo "note: mystmd not on PATH — run 'npm install -g mystmd' once (see CLAUDE.md)"
            fi
          '';
        };
      });
}
