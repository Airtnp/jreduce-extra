set -e
DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"
NAME=procyon

if [ -z "$PROCYON" ]
then 
    echo "Please set \$PROCYON to be the absolute path to the $NAME jar";
    exit -1
fi

# output="$(realpath $NAME)"
output="$DIR/$NAME"
rm -rf "$output"
FLAGS=""

mkdir -p "$output/src"
(
  cd "$1"; 
  export LC_ALL=C
  find . -name '*.class' -print | sed 's/\.\///' | sort > "$output/classes.txt";
  jar cf "$output/input.jar" "@$output/classes.txt"
)
shift

java -jar "$PROCYON" "$output/input.jar" -o "$output/src" &> "$output/$NAME.log"

# bash "$DIR/compile.sh" "$(realpath --relative-to=. "$output")" "$1"
# echo "$(realpath --relative-to=. "$output")"
echo $output