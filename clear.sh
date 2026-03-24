# clear corpus
rm -rf .cifuzz-corpus/com.example.SnakeYamlFuzzTest/fuzzYamlParser/*

# clear crash
rm -rf src/test/resources/com/example/SnakeYamlFuzzTestInputs/fuzzYamlParser/*

# maven clean
mvn clean
