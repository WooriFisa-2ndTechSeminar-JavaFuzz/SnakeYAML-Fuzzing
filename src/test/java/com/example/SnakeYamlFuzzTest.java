package com.example;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SnakeYamlFuzzTest {

    static Stream<Arguments> fuzzYamlParser() {
        return Stream.of(
            arguments("name: Jazzer\ntype: Fuzzer"),
            arguments("!!set\n? a\n? b"),
            arguments("&a [ *a ]") // 순환 참조 시드
        );
    }

    @MethodSource
    @FuzzTest
    void fuzzYamlParser(@NotNull String input) {
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(input); 
            
            // 핵심 트리거: 메모리에 올라간 객체를 순회하도록 강제합니다.
            // 만약 순환 참조 객체라면, hashCode()나 toString()을 호출하는 순간 
            // JVM이 무한 루프에 빠지며 StackOverflowError가 터집니다!
            if (parsed != null) {
                parsed.toString();
            }
            
        } catch (Exception e) {
            // YAMLException 뿐만 아니라 파싱 중 흔히 발생하는 모든 Java Exception을 무시합니다.
            // 우리가 잡고자 하는 StackOverflowError나 OutOfMemoryError는 
            // Exception이 아니라 'Error' 타입이므로 이 catch 블록을 무사히 통과하여 
            // Jazzer에게 '크래시'로 정상 보고됩니다.
        }
    }
}