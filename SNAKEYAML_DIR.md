```
SnakeYAML-Fuzzing/
├──.cifuzz-corpus/
│   └── com.example.SnakeYamlFuzzTest
│       └── fuzzYamlParser
│           ├── ff340d4de2dbfd317fcaf870cdc68afedd99d95f
│           ├── ff3b22236e4689cad391cd4c5cfbf188a3116edb
│           ├── ff7b274f933d53129e3e0684e3147c5657c7e503
│           ├── ff80b9da550334f6cca6c8f6549450563d41b73a

(생략)

│
├── clear.sh
├── fuzz_script.sh
├── pom.xml
├── src
│   ├── java
│   │   └── org
│   │       └── yaml
│   │           └── snakeyaml

(생략)

│   └── test
│       ├── java
│       │   └── com
│       │       └── example
│       │           └── SnakeYamlFuzzTest.java
│       └── resources
│           └── com
│               └── example
│                   └── SnakeYamlFuzzTestInputs
│                       └── fuzzYamlParser
│                           ├── crash-1f93a417271a94447649ffaf78d00b48b6200ed1
│                           ├── crash-4f8d2549fba84a23a11052018c5b604f2a2587ca
└── target
    ├── site
    │   └── jacoco
    │       ├── index.html

(생략)

    ├── surefire-reports
    │   └── com.example.SnakeYamlFuzzTest.txt

(생략)

```