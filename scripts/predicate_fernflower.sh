set -e
DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"
NAME=fernflower

if [ -z "$FERNFLOWER" ]
then 
    echo "Please set \$FERNFLOWER to be the absolute path to the $NAME jar";
    exit -1
fi

# output="$(realpath $NAME)"
output="$DIR/$NAME"
rm -rf "$output"
FLAGS="-dgs=1"

mkdir -p "$output/src"
(
  cd "$1"; 
  export LC_ALL=C
  find . -name '*.class' -print | sed 's/\.\///' | sort > "$output/classes.txt";
  jar cf "$output/input.jar" "@$output/classes.txt"
)

shift

mkdir -p "$output/output"
java -jar "$FERNFLOWER" $FLAGS "$output/input.jar" "$output/output" > "$output/$NAME.log"
unzip -qo "$output/output/input.jar" -d "$output/src"

# bash "$DIR/compile.sh" "$(realpath --relative-to=. "$output")" "$1"
# echo "$(realpath --relative-to=. "$output")"
echo $output