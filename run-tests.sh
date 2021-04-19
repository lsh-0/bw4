#!/bin/bash
set -e

# always ratchet *upwards*
fail_threshold=70

# this file can't live in src/strongbox because lein-cloverage can't be found during dev.
# so we copy it in and destroy it afterwards
cp cloverage.clj src/bw/cloverage.clj

function finish {
  rm src/bw/cloverage.clj
  lein clean
}
trap finish EXIT

if which xvfb-run; then
    # CI
    xvfb-run lein cloverage --runner "boardwalk" --fail-threshold "$fail_threshold" --html
else
    lein cloverage --runner "boardwalk" --fail-threshold "$fail_threshold" --html || {
        retval="$?"
        # 1 for failed tests
        # 253 for failed coverage
        if [ "$retval" = "253" ]; then
            if which otter-browser; then
                otter-browser "file://$(pwd)/target/coverage/index.html" &
            fi
        fi
        exit "$retval"
    }
fi
