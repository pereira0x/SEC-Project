{
  description = "SEC Project";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = import nixpkgs { inherit system; };
      in {
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [ jdk21 maven ];
          shellHook = ''
            export JAVA_HOME=${pkgs.jdk21}
            PATH="${pkgs.jdk21}/bin:$PATH"
          '';
        };
      });
}
