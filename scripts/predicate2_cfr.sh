set -e
DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"
NAME=cfr

if [ -z "$CFR" ]
then 
    echo "Please set \$CFR to be the absolute path to the $NAME jar";
    exit -1
fi

output="$(realpath $NAME)"
rm -rf "$output"
FLAGS="--caseinsensitivefs true"

mkdir -p "$output/src"
(
  cd "$1"; 
  export LC_ALL=C
  find . -name '*.class' -print | sed 's/\.\///' | sort > "$output/classes.txt";
  jar cf "$output/input.jar" @"$output/classes.txt"
)
shift

java -jar "$CFR" $FLAGS "$output/input.jar" --outputdir "$output/src" &> "$output/$NAME.log"

bash "$DIR/utils/compile.sh" "$(realpath --relative-to=. "$output")" "$1"
