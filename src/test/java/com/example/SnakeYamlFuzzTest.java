package com.example;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SnakeYamlFuzzTest {

    // 초기 시드 데이터 제공 (MethodSource)
    static Stream<Arguments> provideSeedData() {
        return Stream.of(
            arguments("name: Jazzer\ntype: Fuzzer"), // 1. 평범한 키-밸류 시드
            arguments("!!set\n? a\n? b"),           // 2. 특수 태그를 포함한 시드
            arguments("&a [ *a ]")                   // 3. 앵커(&)와 에일리어스(*) 참조 시드
        );
    }

    @MethodSource("provideSeedData")
    @FuzzTest
    void fuzzYamlParser(@NotNull String input) {
        try {
            Yaml yaml = new Yaml();
            yaml.load(input); 
        } catch (org.yaml.snakeyaml.error.YAMLException | IllegalArgumentException e) {
            // 단순 문법 오류나 파싱 실패는 무시합니다.
            // 우리가 찾는 타겟은 JVM을 터뜨리는 StackOverflowError나 OutOfMemoryError입니다.
            // 이러한 치명적인 시스템 에러(Error)는 catch 블록에 잡히지 않고 Jazzer에 의해 크래시로 기록됩니다.
        }
    }
}
