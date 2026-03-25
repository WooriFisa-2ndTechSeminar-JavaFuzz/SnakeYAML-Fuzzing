# maven install - skip tests
mvn install -DskipTests

# fuzzing(bash)
JAZZER_FUZZ=1 mvn test -Dmaven.test.failure.ignore=true -Djazzer.keep_going=0 -Dtest=SnakeYamlFuzzTest

# 확실하지 않음
JAZZER_ARGS="--keep_going=0" JAZZER_FUZZ=1 mvn test -Dtest=SnakeYamlFuzzTest
$env:JAZZER_ARGS="--keep_going=0"; $env:JAZZER_FUZZ="1"; mvn test -Dtest=SnakeYamlFuzzTest