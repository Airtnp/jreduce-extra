cd target;
output="$(java Main)"

if [[ $output =~ "expected output" ]]; then
    exit 0
else
    exit 1
fi