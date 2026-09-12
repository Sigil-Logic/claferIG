ifeq ($(OS),Windows_NT)
EXE := .exe
endif

# Alloy is acquired from Maven Central, pinned to an exact version and
# verified against a SHA-256 (Sigil-Logic/clafer#5).  The dist jar bundles
# the per-platform native solvers (including darwin/arm64) and self-extracts
# them at run time, so no separate native libraries are staged.
ALLOY_VERSION := 6.2.0
ALLOY_JAR := org.alloytools.alloy.dist-$(ALLOY_VERSION).jar
ALLOY_URL := https://repo1.maven.org/maven2/org/alloytools/org.alloytools.alloy.dist/$(ALLOY_VERSION)/$(ALLOY_JAR)
ALLOY_SHA256 := 6037cbeee0e8423c1c468447ed10f5fcf2f2743a2ffc39cb1c81f2905c0fdb9d

# Calling `make` should only build
all: alloyIG.jar build

# Calling `make install to=<target directory>` should only install
install:
	mkdir -p $(to)
	cp -f $(ALLOY_JAR) $(to)
	cp -f alloyIG.jar $(to)
	cp -f LICENSE $(to)/
	cp -f CHANGES.md $(to)/claferIG-CHANGES.md
	cp -f README.md $(to)/claferIG-README.md
	cp `stack path --local-install-root`/bin/claferIG$(EXE) $(to)

# Build takes less time. For ease of development.
build: alloyIG.jar
	stack build
	cp alloyIG.jar `stack path --local-install-root`/bin/
	cp $(ALLOY_JAR) `stack path --local-install-root`/bin/

# alloyIG.jar is built from source (it is not committed); --release 17
# matches the Alloy 6.2.0 class-file level and the CI toolchain, so a newer
# local JDK cannot produce classes an older runtime refuses to load.
alloyIG.jar: $(ALLOY_JAR) src/manifest src/org/clafer/ig/AlloyIG.java src/org/clafer/ig/Util.java src/org/clafer/ig/AlloyIGException.java
	mkdir -p dist/javabuild
	javac --release 17 -cp "$(ALLOY_JAR)" -d dist/javabuild src/org/clafer/ig/AlloyIG.java src/org/clafer/ig/Util.java src/org/clafer/ig/AlloyIGException.java
	jar cfm alloyIG.jar src/manifest -C dist/javabuild org/clafer/ig/

.PHONY : test

test:
	stack test --no-run-tests
	cp alloyIG.jar `stack path --dist-dir`/build/test-suite/
	cp $(ALLOY_JAR) `stack path --dist-dir`/build/test-suite/
	cp alloyIG.jar `stack path --dist-dir`/build/claferIG/
	cp $(ALLOY_JAR) `stack path --dist-dir`/build/claferIG/
	stack test

clean:
	stack clean
	rm -f alloyIG.jar
	rm -rf dist/javabuild
	rm -rf tools

tags:
	hasktags --ctags --extendedctag .

codex:
	codex update
	mv codex.tags tags

$(ALLOY_JAR):
	@echo "Fetching Alloy $(ALLOY_VERSION) from Maven Central..."
	curl -fsSL -o "$(ALLOY_JAR)" "$(ALLOY_URL)"
	@if command -v shasum > /dev/null 2>&1; then \
		echo "$(ALLOY_SHA256)  $(ALLOY_JAR)" | shasum -a 256 -c - ; \
	else \
		echo "$(ALLOY_SHA256)  $(ALLOY_JAR)" | sha256sum -c - ; \
	fi || { echo "[ERROR] $(ALLOY_JAR) checksum mismatch"; rm -f "$(ALLOY_JAR)"; false; }
