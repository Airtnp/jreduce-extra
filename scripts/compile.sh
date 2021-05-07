set -o pipefail

function compile_all () {
  mkdir -p "$1"
  cd "$2"
  javac -encoding utf8 -Xmaxwarns 0 -Xmaxerrs ${MAX_ERRORS:-1000} \
      -nowarn -cp "$libs":"$classpath":"$1" -d "$1" \
      "@$(realpath --relative-to=. "$output/sourcefiles.txt")"  2>&1 
  X="$?"
  if [[ "$X" == "1" ]]; 
  then 
    return 0
  else 
    return 1
  fi
}

output="$(realpath ${1:?output not set})"
libs="${2:?libs not set}"

export LC_ALL=C
find "$output/src" -name *.java | sed "s|$output/src/||" | sort > "$output/sourcefiles.txt"

compile_all "$output/classes" "$output/src" > "$output/compiler.out.txt"
RET=$?

if [[ ! -z "$3" ]]
then
  compile_all "$output/cmp-classes" "$3" > "$output/cmp-compiler.out.txt"
  comm -23 <(sort "$output/compiler.out.txt") <(sort "$output/cmp-compiler.out.txt")
else
  # sed 's/[0-9]//g' "$output/compiler.out.txt"
  sed -n 's/^\([^.]*.java\):[0-9]*: error/\1/gp' "$output/compiler.out.txt" | sort
fi

exit "$RET"