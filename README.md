# SnakeYAML-Fuzzing

## 📝 프로젝트 소개
본 프로젝트는 보안 취약점이 발견된 SnakeYAML 1.30 버전을 대상으로 직접 그레이박스 퍼징(Graybox Fuzzing)을 수행하고 소스코드 단위에서 취약점의 근본 원인을 분석하는 프로젝트이다. 이를 통해 소프트웨어 안정성 확보의 중요성을 이해하고, 안전한 자바 애플리케이션 작성을 위한 시큐어 코딩(Secure Coding) 기법을 학습하는 것을 목적으로 한다.

## 💡 핵심 개념
* **그레이박스 퍼징 (Graybox Fuzzing):** 프로그램의 내부 구조(코드 커버리지 등)를 관찰하면서, 더 많은 코드 경로를 탐색할 수 있도록 지능적으로 변형된 무작위 입력값을 주입하여 버그와 취약점을 찾아내는 소프트웨어 테스트 기법이다.
* **Jazzer:** Java 가상 머신(JVM) 기반 애플리케이션을 타겟으로 하는 커버리지 기반 퍼징 도구로, JNI 및 복잡한 자바 코드의 버그를 효과적으로 탐지한다.
* **JaCoCo:** Java 코드의 테스트 커버리지를 측정하고 시각화된 리포트를 제공하는 오픈소스 도구이다. 

## 🛠️ 사용 기술 스택
* **Language:** ![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
* **Testing & Analysis:** ![Jazzer](https://img.shields.io/badge/Jazzer-4B32C3?style=for-the-badge&logo=jazzer&logoColor=white)
![JaCoCo](https://img.shields.io/badge/JaCoCo-E10098?style=for-the-badge)
* **Build Tool:** ![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

## 🏗️ 프로젝트 구조
```plaintext
SnakeYAML-Fuzzing/
├──.cifuzz-corpus/  # 코퍼스
│   └── com.example.SnakeYamlFuzzTest
│       └── fuzzYamlParser
│           ├── ff340d4de2dbfd317fcaf870cdc68afedd99d95f
│           ├── ff3b22236e4689cad391cd4c5cfbf188a3116edb
│           ├── ff7b274f933d53129e3e0684e3147c5657c7e503
│           ├── ff80b9da550334f6cca6c8f6549450563d41b73a

(생략)

│
├── clear.sh  # 코퍼스/크래시 데이터 삭제, 메이븐 클린 등 정리 스크립트
├── fuzz_script.sh  # Jazzer 퍼징/회귀테스트 명령어 등 실행 스크립트
├── pom.xml  # 메이븐 의존성
├── src
│   ├── java
│   │   └── org
│   │       └── yaml
│   │           └── snakeyaml  # SnakeYAML 1.30 소스코드 원본

(생략)

│   └── test
│       ├── java
│       │   └── com
│       │       └── example
│       │           └── SnakeYamlFuzzTest.java  # 테스트 클래스
│       └── resources
│           └── com
│               └── example
│                   └── SnakeYamlFuzzTestInputs  # 크래시
│                       └── fuzzYamlParser
│                           ├── crash-1f93a417271a94447649ffaf78d00b48b6200ed1
│                           ├── crash-4f8d2549fba84a23a11052018c5b604f2a2587ca
└── target
    ├── site
    │   └── jacoco  # JaCoCo 커버리지 리포트
    │       ├── index.html

(생략)

    ├── surefire-reports
    │   └── com.example.SnakeYamlFuzzTest.txt  # 회귀 테스트 결과 에러 로그

(생략)
```

- **.cifuzz-corpus/:**
    - 퍼징 과정에서 생성된 코퍼스가 모이는 디렉토리
- **src/java:**
    - 퍼징 대상 프로그램 소스코드(SnakeYAML)을 두는 디렉토리
- **src/test/java:**
    - 테스트 하네스 소스코드(SnakeYamlFuzzTest.java)를 두는 디렉토리
- **src/test/resources/<패키지명>/<테스트 클래스>Inputs:**
    - 퍼징 과정에서 생성된 크래시가 모이는 디렉토리
- **target/site/jacoco:**
    - JaCoCo 커버리지 리포트가 만들어지는 디렉토리
- **target/surefire-reports:**
    - 메이븐 빌드 로그가 모이는 디렉토리

## ▶️ 테스트 실행 방법

```bash
# 테스트 및 퍼징 실행(환경에 맞게 스크립트 사용)
./fuzz_script.sh

# 테스트 초기화
./clear.sh
```

> 💡 **회귀 테스트 방법**\
> `pom.xml`에서 사용 중인 Jazzer는 Standalone이 아니라, 메이븐 테스트(mvn test)로 Jazzer를 실행할 수 있게 하는, Junit 통합 버전이다.
> Junit 통합 Jazzer는 `mvn test`시 `JAZZER_FUZZ=1` 환경변수를 입력하면 `그레이박스 퍼징 모드`, 환경변수 없이 실행하면 `회귀 테스트 모드`로 동작한다.
> 현재 `pom.xml`은 `JAZZER_FUZZ=1` 환경변수를 기본 주입하고 있다. 따라서 회귀 테스트를 하고 싶다면 먼저 `pom.xml`에서 이 환경변수를 지워야 한다.
> `pom.xml`과 `fuzz_script.sh`의 주석을 참고할 것

생성된 커버리지 리포트는 `target/site/jacoco/index.html`에서 확인할 수 있다.

## 🚨 SnakeYAML 1.30 취약점 개요
SnakeYAML 1.30 버전에서는 신뢰할 수 없는 사용자의 입력값을 파싱할 때 치명적인 보안 문제가 발생한다. 본 프로젝트에서는 CVE에 정식 보고된 다음 두 가지 주요 취약점을 다룬다.
1. **RCE (원격 코드 실행):** 악성 YAML 문서를 통해 서버에서 임의의 명령어가 실행될 수 있는 역직렬화(Deserialization) 취약점이다.
2. **StackOverflow (DoS):** 깊게 중첩된 악성 YAML 문서를 파싱할 때 재귀 호출 누적으로 인한 스택 메모리 고갈로 서버가 다운되는 서비스 거부 취약점이다.

## 🔍 취약점 원인 및 패치 분석

### 1. 생성된 크래시 예시
#### `crash-1f93a417271a94447649ffaf78d00b48b6200ed1`
```yaml
[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[ (생략) ]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]
```

- 에러 유형: Stack Overflow
- 의심 코드
    - Composer.java
        1. `org.yaml.snakeyaml.composer.Composer.composeSequenceNode(Composer.java:186)`
        2. `org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:245)`
        3. `org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:281)`
        4. `org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:188)`
        5. `org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:314)`
        6. `org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:305)`
- 주요 에러 메세지

```
caused by: java.lang.StackOverflowError
	at org.yaml.snakeyaml.composer.Composer.composeSequenceNode(Composer.java:240)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:186)
	at org.yaml.snakeyaml.composer.Composer.composeSequenceNode(Composer.java:245)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:186)
	at org.yaml.snakeyaml.composer.Composer.composeSequenceNode(Composer.java:245)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:186)
	at org.yaml.snakeyaml.composer.Composer.composeSequenceNode(Composer.java:245)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:186)
```

```
Caused by: java.lang.StackOverflowError
	at org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:281)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:188)
	at org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:314)
	at org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:305)
	at org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:286)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:188)
	at org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:314)
	at org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:305)
	at org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:286)
	at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:188)
	at org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:314)
	at org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:305)
```

### `crash-891f86f65c42b0bd36a97d0ee60a67e6fcb01bfe`

```yaml
!!jaz.Zer
cla.beAroperocylass' }
```

- 에러 유형: RCE
- 의심코드:
    - Constructor.java
        1. `getConstructor(Node node)`
        2. `getClassForNode(Node node)`
        3. `getClassForName(String name)`

```
Unrestricted class/object creation based on externally controlled data may allow
remote code execution depending on available classes on the classpath.
	at org.yaml.snakeyaml.constructor.Constructor$ConstructScalar.construct(Constructor.java:417)
	at org.yaml.snakeyaml.constructor.Constructor$ConstructYamlObject.construct(Constructor.java:332)
	at org.yaml.snakeyaml.constructor.BaseConstructor.constructObjectNoCheck(BaseConstructor.java:231)
	at org.yaml.snakeyaml.constructor.BaseConstructor.constructObject(BaseConstructor.java:220)
	at org.yaml.snakeyaml.constructor.BaseConstructor.constructDocument(BaseConstructor.java:174)
	at org.yaml.snakeyaml.constructor.BaseConstructor.getSingleData(BaseConstructor.java:158)
	at org.yaml.snakeyaml.Yaml.loadFromReader(Yaml.java:491)
	at org.yaml.snakeyaml.Yaml.load(Yaml.java:416)
	... 129 more
```

### 2. RCE (원격 코드 실행) 취약점
* **원인 요약:** SnakeYAML 1.30은 YAML 글로벌 태그에 포함된 클래스명을 신뢰하고 로드한다. 이로 인해 공격자가 의도한 클래스를 역직렬화 경로로 진입시킬 수 있다.

#### 취약 코드 (v1.30)

```java
// Constructor.java (v1.30)
protected Class<?> getClassForNode(Node node) {
	Class<? extends Object> classForTag = typeTags.get(node.getTag());
	if (classForTag == null) {
		String name = node.getTag().getClassName(); // YAML 입력값
		Class<?> cl;
		try {
			cl = getClassForName(name);              // 검증 없는 클래스 로딩
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
		return Class.forName(name);
	}
}
```

위 로직은 태그 문자열을 기반으로 `Class.forName`을 직접 호출하므로, `!!java.lang.ProcessBuilder` 같은 입력이 들어오면 위험한 클래스 로딩 경로가 열리게 된다.

#### 수정 코드 (v2.6)

```java
// LoaderOptions.java (v2.6)
// 2.6 버전에는 TagInspector 클래스가 추가됨
private TagInspector tagInspector = new UnTrustedTagInspector();
```

```java
// UnTrustedTagInspector.java (v2.6)
@Override
public boolean isGlobalTagAllowed(Tag tag) {
  // 글로벌 태그를 기본적으로 항상 차단
  return false;
}
```

```java
// Composer.java (v2.6)
nodeTag = new Tag(tag);
if (nodeTag.isCustomGlobal()
	&& !loadingConfig.getTagInspector().isGlobalTagAllowed(nodeTag)) {
  throw new ComposerException("Global tag is not allowed: " + tag, startMark);
}
```

핵심 변경점은 `Constructor`에서 막는 것이 아니라 더 이른 `Composer` 단계에서 글로벌 태그를 차단한다는 점이다. 따라서 공격 입력은 클래스 로딩 단계 전에 예외로 중단된다.

### 3. StackOverflow (서비스 거부) 취약점
* **원인 요약:** SnakeYAML 1.30은 컬렉션 중첩 깊이에 대한 제한이 없어 재귀 호출이 무한히 깊어질 수 있다.

#### 취약 코드 (v1.30)

```java
// Composer.java (v1.30)
private Node composeSequenceNode(String anchor) {
	while (!parser.checkEvent(Event.ID.SequenceEnd)) {
		blockCommentsCollector.collectEvents();
		if (parser.checkEvent(Event.ID.SequenceEnd)) {
			break;
		}
		children.add(composeNode(node)); // composeNode 재귀 호출
	}
}
```

```java
// Composer.java (v1.30)
private Node composeNode(Node parent) {
	if (parser.checkEvent(Event.ID.Scalar)) {
		node = composeScalarNode(anchor, blockCommentsCollector.consume());
	} else if (parser.checkEvent(Event.ID.SequenceStart)) {
		node = composeSequenceNode(anchor); // composeSequenceNode 재귀 호출
	} else {
		node = composeMappingNode(anchor);
	}
}
```

`composeSequenceNode -> composeNode -> composeSequenceNode` 또는 `composeMappingNode -> composeNode -> composeMappingNode` 경로가 깊어지면 JVM 스택이 고갈된다.

#### 수정 코드 (v2.6)

```java
// LoaderOptions.java (v2.6)
private int nestingDepthLimit = 50; // 기본 깊이 상한
```

```java
// Composer.java (v2.6)
private int nestingDepth = 0;
private final int nestingDepthLimit;

private void increaseNestingDepth() {
	if (nestingDepth > nestingDepthLimit) {
        // 깊이 초과시 예외 발생
		throw new YAMLException("Nesting Depth exceeded max " + nestingDepthLimit);
	}
	nestingDepth++;
}

private void decreaseNestingDepth() {
	if (nestingDepth > 0) {
		nestingDepth--;
	} else {
        // 깊이 값이 음수일 경우 예외 발생
		throw new YAMLException("Nesting Depth cannot be negative");
	}
}
```

```java
// Composer.java (v2.6)
increaseNestingDepth();
if (parser.checkEvent(Event.ID.Scalar)) {
	node = composeScalarNode(anchor, blockCommentsCollector.consume());
} else if (parser.checkEvent(Event.ID.SequenceStart)) {
	node = composeSequenceNode(anchor);
} else {
	node = composeMappingNode(anchor);
}
decreaseNestingDepth();
```

재귀 구조는 유지하지만, 깊이 카운터와 상한선을 통해 StackOverflow 이전에 안전하게 실패하도록 변경되었다.

## 🧪 퍼징 관찰 포인트
* Jazzer 실행 중 RCE 경로는 `Constructor` 내부 클래스 로딩/리플렉션 구간을 반복적으로 탐색한다.
    * **1.30 보안 취약점 요약**: 입력받은 객체를 로딩하기 전, 신뢰할 수 있는 객체인지를 검증하는 로직 부재.
    * **2.6 개선 요약**: RCE 공격의 가장 주된 요인인 글로벌 태그의 입력을 기본 차단하고, 필요할 경우 개발자가 직접 allowlist를 만들어야 함.
* StackOverflow 경로는 `Composer`의 sequence/mapping 재귀 지점에서 커버리지가 집중된다.
    * **1.30 보안 취약점 요약**: 재귀 호출의 횟수를 제한하는 로직 부재.
    * **2.6 개선 요약**: 재귀 호출 횟수를 제한하고 초과 시, 즉시 예외가 발생하도록 함.

## 🔐 보안 코딩 권고
* SnakeYAML 구버전을 사용 중일 경우, 반드시 최신 버전으로 업데이트 해야한다.
* 역직렬화 기능이 필요할 경우 신뢰할 수 있는 객체에 대해서만 역직렬화가 수행되도록 하는 로직이 반드시 필요하다.
    * SnakeYAML 2.6의 경우 가능하면 `SafeConstructor` 사용을 우선하고, 커스텀 타입이 필요하면 직접 'allowlist'를 구성해야 한다.
    * 예시: 
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
    ```
* 재귀/순환 호출 기능이 필요할 경우 반드시 호출 횟수를 제한하는 로직이 필요하다.
    * SnakeYAML 2.6의 경우 가능하면 기본 깊이 값(50)을 그대로 사용하며, 깊이 상한을 높이고 싶다면 개발자가 직접 수정해야 함.
    * 예시:
    ```java
	LoaderOptions options = new LoaderOptions();

	// Setter 메서드로 최대 중첩 깊이를 직접 조정
	// 기본값은 50이며, 아래 예시는 80으로 변경
	options.setNestingDepthLimit(80);

	// 변경된 LoaderOptions를 Constructor에 주입
	Yaml yaml = new Yaml(new Constructor(options));
    ```

## 📚 참고 문서
* [CVE-2022-1471](https://www.cve.org/CVERecord?id=CVE-2022-1471)
* [CVE-2022-38749](https://www.cve.org/CVERecord?id=CVE-2022-38749)
* [CVE-2022-38750](https://www.cve.org/CVERecord?id=CVE-2022-38750)
* [CVE-2022-38751](https://www.cve.org/CVERecord?id=CVE-2022-38751)
