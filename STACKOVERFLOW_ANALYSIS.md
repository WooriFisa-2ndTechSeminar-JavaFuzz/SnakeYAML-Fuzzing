# SnakeYAML Stack Overflow 보안 취약점 분석: v1.30 vs v2.6

## 개요
그레이박스 퍼징으로 발견된 Crash는 SnakeYAML 1.30의 `Composer` 단계에서 발생한 깊은 재귀 호출 누적으로 인한 StackOverflow이다.

관찰된 반복 호출 흐름은 다음 두 가지이다.

1. `composeSequenceNode` <-> `composeNode` 반복
2. `composeMappingNode` -> `composeMappingChildren` -> `composeValueNode` -> `composeNode` 반복

핵심 원인은 1.30에서 컬렉션 중첩 깊이에 대한 상한 검사가 없다는 점이며, 2.6에서는 `nestingDepthLimit` 기반 방어 로직이 추가되어 동일 입력이 StackOverflow 전에 `YAMLException`으로 차단된다.

---

## 먼저 이해해야 할 전체 실행 흐름

SnakeYAML 로딩 파이프라인을 단순화하면 아래와 같다.

1. `Parser`가 YAML 토큰/이벤트 스트림을 순서대로 제공
2. `Composer`가 이벤트를 재귀적으로 소비하여 Node 그래프 생성
3. `Constructor`가 Node 그래프를 Java 객체로 변환

이번 Crash는 2단계(`Composer`)에서 발생했다. 즉, 객체 생성 이전에 이미 재귀 깊이가 과도하게 커져 스택이 고갈된 사례다.

### Composer 내부에서 이벤트가 소비되는 방식

- `composeNode(parent)`:
    - 현재 이벤트를 보고 분기한다.
    - `Scalar`면 `composeScalarNode`
    - `SequenceStart`면 `composeSequenceNode`
    - 그 외(`MappingStart`)면 `composeMappingNode`
- `composeSequenceNode`:
    - `SequenceEnd`를 만날 때까지 자식마다 `composeNode` 재귀 호출
- `composeMappingNode`:
    - `MappingEnd`를 만날 때까지 key/value를 각각 `composeNode`로 재귀 호출

즉, 문서 구조가 깊어질수록 호출 스택도 거의 같은 깊이로 커지는 구조다.

---

## 주요 변수/메서드 역할 설명

아래는 분석에 직접 관련된 멤버만 추린 것이다.

### 1.30 `Composer` 기준

- `parser`
    - YAML 이벤트 스트림 공급자.
    - `checkEvent(...)`로 다음 이벤트 타입 확인, `getEvent()`로 이벤트 소비.

- `anchors`
    - `&a` 같은 anchor 이름과 생성된 Node를 매핑.
    - alias(`*a`)를 만나면 이 맵에서 기존 Node를 재사용.

- `recursiveNodes`
    - 현재 재귀 경로 상의 Node 추적용 집합.
    - alias가 현재 경로를 역참조하면 `twoStepsConstruction` 처리에 사용.

- `nonScalarAliasesCount`
    - scalar가 아닌 alias 사용 횟수 카운트.
    - `maxAliasesForCollections` 초과 시 예외를 던져 alias 폭탄을 방어.

- `composeNode(parent)`
    - 모든 재귀 파싱의 관문.
    - 현재 이벤트 타입으로 scalar/sequence/mapping 처리기로 분기.

- `composeSequenceNode(anchor)`
    - sequence 시작 이벤트를 소비하고, 끝날 때까지 항목을 재귀적으로 구성.

- `composeMappingNode(anchor)`
    - mapping 시작 이벤트를 소비하고, 끝날 때까지 key/value 쌍을 재귀적으로 구성.

- `composeMappingChildren(children, node)`
    - 한 쌍의 key/value를 생성해 `children`에 추가.
    - 내부적으로 key/value 둘 다 `composeNode` 재귀 호출을 유발.

### 2.6에서 추가/강화된 핵심 멤버

- `nestingDepth`
    - 현재 컬렉션 중첩 깊이 카운터.

- `nestingDepthLimit`
    - 허용 최대 깊이. 기본값은 `LoaderOptions`에서 50.

- `increaseNestingDepth()` / `decreaseNestingDepth()`
    - `composeNode`의 non-alias 분기에서 진입/종료 시 호출.
    - 초과 시 즉시 `YAMLException("Nesting Depth exceeded max ...")` 발생.

결론적으로 1.30은 alias 수 제한은 있었지만, 중첩 깊이 제한은 없었다. 2.6은 깊이 제한을 추가해 재귀 DoS를 막는다.

---

## 호출 흐름을 코드 관점으로 다시 읽기

### 흐름 A: Sequence 중첩 폭증

1. `composeNode`가 `SequenceStart` 감지
2. `composeSequenceNode` 진입
3. 각 원소마다 `children.add(composeNode(node))` 호출
4. 원소가 다시 sequence면 1~3 반복

1.30에서는 반복 횟수(=중첩 깊이)에 상한이 없어 StackOverflow로 종료된다.

### 흐름 B: Mapping 중첩 폭증

1. `composeNode`가 `MappingStart` 감지
2. `composeMappingNode` 진입
3. `composeMappingChildren`에서 key/value 각각 `composeNode` 호출
4. value가 mapping이면 1~3 반복

mapping은 key/value 두 경로 모두 재귀가 걸릴 수 있어, 공격 입력에 따라 sequence보다 더 빠르게 스택을 소모할 수 있다.

---

## Crash 흐름 1 분석: Sequence 중심 재귀

### 1.30 취약 흐름

`composeSequenceNode`는 시퀀스 아이템을 읽어들이며, `SequenceEnd`가 False일 때 마다 `composeNode`를 재귀 호출한다.

- Composer.java, 라인 218, `Node composeSequenceNode(String)`

```java
    while (!parser.checkEvent(Event.ID.SequenceEnd)) {
        blockCommentsCollector.collectEvents();
        if (parser.checkEvent(Event.ID.SequenceEnd)) {
            break;
        }
        children.add(composeNode(node));
    }
```


`composeNode`는 다음 이벤트가 `SequenceStart`이면 다시 `composeSequenceNode`를 호출하며 일련의 과정이 반복된다.

- Composer.java, 라인 156, `Node composeNode(Node)`

```java
    if (parser.checkEvent(Event.ID.Scalar)) {
        node = composeScalarNode(anchor, blockCommentsCollector.consume());
    } else if (parser.checkEvent(Event.ID.SequenceStart)) {
        node = composeSequenceNode(anchor);
    } else {
        node = composeMappingNode(anchor);
    }
```

결과적으로 매우 깊은 중첩 sequence 입력에서 재귀 깊이가 계속 증가하고, 종료 전에 JVM 스택이 고갈되어 StackOverflow가 발생한다.

---

## Crash 흐름 2 분석: Mapping 중심 재귀

### 1.30 취약 흐름

`composeMappingNode`는 mapping이 끝날 때까지 `composeMappingChildren`를 반복 호출한다.

- Composer.java, 라인 259, `Node composeMappingNode(String)`

```java
    while (!parser.checkEvent(Event.ID.MappingEnd)) {
        blockCommentsCollector.collectEvents();
        if (parser.checkEvent(Event.ID.MappingEnd)) {
            break;
        }
        composeMappingChildren(children, node);
    }
```

`composeMappingChildren`는 key/value를 각각 `composeNode`로 위임한다.

- Composer.java, 라인 300, void `composeMappingChildren(List<NodeTuple>, MappingNode)`

```java
    protected void composeMappingChildren(List<NodeTuple> children, MappingNode node) {
        Node itemKey = composeKeyNode(node);
        if (itemKey.getTag().equals(Tag.MERGE)) {
            node.setMerged(true);
        }
        Node itemValue = composeValueNode(node);
        children.add(new NodeTuple(itemKey, itemValue));
    }
```

`composeValueNode`와 `composeKeyNode`는 다시 `composeNode`를 호출한다.

- Composer.java. 라인 309, `Node composeKeyNode(MappingNode)`

```java
    protected Node composeKeyNode(MappingNode node) {
        return composeNode(node);
    }
```

- Composer.java, 라인 313, `Node composeValueNode(MappingNode)`

```java
    protected Node composeValueNode(MappingNode node) {
        return composeNode(node);
    }
```

이때 value가 다시 mapping이면 `composeNode -> composeMappingNode`로 재진입하므로, 깊은 mapping 중첩 입력에서 동일하게 스택이 누적되어 StackOverflow가 발생한다.

---

## 최신 2.6에서의 개선 포인트

2.6은 재귀 구조 자체를 제거한 것이 아니라, Compose 단계에 깊이 제한을 도입해 DoS성 입력을 조기 차단한다.

### 1) Composer에 깊이 카운터/제한 추가

- Composer.java, 라인 69

```java
    // keep the nesting of collections inside other collections
    private int nestingDepth = 0;
    private final int nestingDepthLimit;
```

- Composer.java, 라인 99

```
    nestingDepthLimit = loadingConfig.getNestingDepthLimit();
```

### 2) LoaderOptions에 기본 제한값 제공

- LoaderOptions.java, 라인 39

```java
    private int nestingDepthLimit = 50;
```

- LoaderOptions.java, 라인 209, `int getNestingDepthLimit()`, `void setNestingDepthLimit(int)`

```java
    public final int getNestingDepthLimit() {
        return nestingDepthLimit;
    }

```java
    public void setNestingDepthLimit(int nestingDepthLimit) {
        this.nestingDepthLimit = nestingDepthLimit;
    }
```

### 3) composeNode 진입/종료 시 깊이 관리

- Composer.java, 라인 181, `Node composeNode(Node)`

```java
    } else {
        NodeEvent event = (NodeEvent) parser.peekEvent();
        String anchor = event.getAnchor();
        increaseNestingDepth();
        if (parser.checkEvent(Event.ID.Scalar)) {
            node = composeScalarNode(anchor, blockCommentsCollector.consume());
        } else if (parser.checkEvent(Event.ID.SequenceStart)) {
            node = composeSequenceNode(anchor);
        } else {
            node = composeMappingNode(anchor);
        }
        decreaseNestingDepth();
    }
```
### 4) 제한 초과 시 즉시 예외

- Composer.java, 라인 402, `void increaseNestingDepth()`

```java
    /**
     * Increase nesting depth and fail when it exceeds the denied limit
     */
    private void increaseNestingDepth() {
        if (nestingDepth > nestingDepthLimit) {
            throw new YAMLException("Nesting Depth exceeded max " + nestingDepthLimit);
        }
        nestingDepth++;
    }
```

- Composer.java, 라인 412 `void decreaseNestingDepth()`

```java
    /**
     * Indicate that the collection is finished and the nesting is decreased
     */
    private void decreaseNestingDepth() {
        if (nestingDepth > 0) {
            nestingDepth--;
        } else {
            throw new YAMLException("Nesting Depth cannot be negative");
    }
  }
```

---

## 결론

- SnakeYAML 1.30
  - 재귀 기반 compose 로직은 존재하지만 중첩 깊이 상한이 없다.
  - 공격자가 깊은 중첩 구조를 만들면 StackOverflow를 유발할 수 있다.

- SnakeYAML 2.6
  - 동일한 재귀 파싱 구조를 유지하되, `nestingDepthLimit`로 방어한다.
  - 결과적으로 StackOverflow 전에 `YAMLException`으로 실패하여 서비스 거부(DoS) 위험을 크게 줄였다.

---

## 공식 근거 링크 (CVE / SnakeYAML)

아래 링크들은 본 문서의 핵심 주장(깊은 중첩 입력으로 인한 StackOverflow DoS, 그리고 최신 버전의 깊이 제한 도입)과 직접 연결되는 공식 근거이다.

- NVD CVE-2022-38749  
    https://nvd.nist.gov/vuln/detail/CVE-2022-38749
    - 설명에 untrusted YAML 입력으로 parser가 stackoverflow crash를 일으킬 수 있다고 명시됨

- NVD CVE-2022-38750  
    https://nvd.nist.gov/vuln/detail/CVE-2022-38750
    - 동일 계열의 stackoverflow 기반 DoS 이슈로 보고됨

- NVD CVE-2022-38751  
    https://nvd.nist.gov/vuln/detail/CVE-2022-38751
    - 동일 계열의 stackoverflow 기반 DoS 이슈로 보고됨

- SnakeYAML 공식 변경 이력(소스 저장소 `changes.xml`)  
    https://github.com/snakeyaml/snakeyaml/blob/master/src/changes/changes.xml
    - `issue=525` 항목에 "Restrict nested depth for collections to avoid DoS attacks" 명시
    - 같은 구간에 "Add test for stackoverflow" 명시

- SnakeYAML 이슈 #525 (StackOverflow 관련)  
    https://bitbucket.org/snakeyaml/snakeyaml/issues/525/got-stackoverflowerror-for-many-open

- SnakeYAML 이슈 #526 (OSS-Fuzz StackOverflow)  
    https://bitbucket.org/snakeyaml/snakeyaml/issues/526/stackoverflow-oss-fuzz-47027

- SnakeYAML 이슈 #530 (OSS-Fuzz StackOverflow)  
    https://bitbucket.org/snakeyaml/snakeyaml/issues/530/stackoverflow-oss-fuzz-47039

참고: CVE 항목은 stackoverflow 기반 DoS를 공식적으로 기술하고, SnakeYAML 변경 이력은 해당 계열 공격을 막기 위한 nested depth 제한 도입 사실을 보여준다.