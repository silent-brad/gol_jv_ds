{
  description = "Conway's Game of Life - Java/Spring/Jade4J/Datastar";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        jdk = pkgs.jdk21;
        maven = pkgs.maven.override { jdk_headless = jdk; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            jdk
            maven
          ];

          shellHook = ''
            export JAVA_HOME=${jdk}
            echo "Java $(java -version 2>&1 | head -1)"
            echo "Maven $(mvn --version 2>&1 | head -1)"
          '';
        };

        packages.default = pkgs.stdenv.mkDerivation {
          pname = "game-of-life";
          version = "0.1.0";
          src = ./.;

          nativeBuildInputs = [ maven jdk ];

          buildPhase = ''
            export JAVA_HOME=${jdk}
            mvn package -DskipTests -o
          '';

          installPhase = ''
            mkdir -p $out/bin $out/lib
            cp target/game-of-life-*.jar $out/lib/game-of-life.jar
            cat > $out/bin/game-of-life <<EOF
            #!/bin/sh
            exec ${jdk}/bin/java -jar $out/lib/game-of-life.jar "\$@"
            EOF
            chmod +x $out/bin/game-of-life
          '';
        };
      });
}
