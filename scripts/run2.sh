SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
# WORKDIR=${WORKDIR:-"$(pwd)"}
WORKDIR=$SCRIPTPATH
INPUTDIR=${INPUTDIR:-$SCRIPTPATH}
jreduce -W "$WORKDIR/workfolder2" -v -p out,exit --total-time 86400 --strategy \
  items+logic --output-file "$WORKDIR/reduced2" --stdlib "$INPUTDIR/stdlib.bin" --jre "/usr/local/lib/jvm" \
  $@ --keep-outputs --metrics-file ../metrics.csv --try-initial \
  --ignore-failure --out "$INPUTDIR/expectation" --cp \
  "$INPUTDIR/benchmark"/lib "$INPUTDIR/reduced_extra" \
  "$INPUTDIR/predicate2" {} %"$INPUTDIR/benchmark"/lib
#  --dump

