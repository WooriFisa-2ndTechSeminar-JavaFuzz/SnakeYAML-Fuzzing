# SnakeYAML 1.30 퍼징 시드 분석

## 개요

SnakeYAML 1.30에는 YAML 처리 과정에서 발생할 수 있는 심각한 보안 취약점들이 있습니다. 아래 6개의 시드 파일들은 이러한 알려진 취약점들을 **의도적으로 트리거**하기 위해 설계되었습니다.

---

## 📌 시드 1: Recursive Anchor (자기참조 앵커)

**파일:** `seed_recursive_anchor.yaml`

```yaml
anchor: &anchor 
  anchor: *anchor
```

### 🎯 의도
앵커가 자기 자신을 참조하는 **무한 재귀** 구조

### 🔴 취약점: Stack Overflow
```
구조:
anchor (앵커 정의)
  └─ anchor: *anchor (자신을 참조)
    └─ anchor: *anchor (다시 자신을 참조)
      └─ ... (끝없이 계속)

결과: 
- StackOverflowError 발생
- JVM의 Stack 메모리 고갈
- 프로세스 크래시
```

### 🔍 SnakeYAML 처리 방식
```java
// 이런 식으로 무한 참조 해석을 시도
yaml.load("anchor: &anchor\n  anchor: *anchor")
↓
anchor 정의: Object A
anchor의 값: *anchor → A의 값을 참조
↓
A의 값도 *anchor → A의 값을 참조... (무한 루프)
↓
Stack Frame 계속 쌓임 → StackOverflow
```

### ⚠️ 실제 악용 사례
공격자가 서버에 이 YAML을 전송하면:
- 서버의 YAML 파서가 무한 재귀 시도
- CPU 사용량 급증 (재귀 처리 반복)
- 최종적으로 서버 프로세스 강제 종료

---

## 📌 시드 2: Deep Nesting (깊은 중첩)

**파일:** `seed_deep_nesting.yaml`

```yaml
a: 
  b: 
    c: 
      d: 
        e: 
          f: 
            g: 
              h: 
                i: 
                  j: 
                    k: 
                      l: 
                        m: 
                          n: 
                            o: 
                              p: 
                                q: test
```

### 🎯 의도
**깊이 20단계 이상의 중첩된 맵** 구조

### 🔴 취약점: Stack Overflow (네스팅)
```
구조:
a
  └─ b
    └─ c
      └─ d
        ... (17단계 더)
          └─ q: test

문제:
- 각 레벨마다 Stack Frame 생성
- 20단계 = 20개의 Stack Frame
- 매우 깊은 YAML은 파싱 중 Stack 고갈

임계값:
- 일반적으로 100-500단계에서 오버플로우 발생
- 시드는 20단계로 설정 (Jazzer가 변형하며 깊이 증가)
```

### 🔍 파서의 동작
```
parseMapping()
  └─ parseMapping()    // a:
    └─ parseMapping()  // b:
      └─ parseMapping()// c:
        ... 반복
```

### ⚠️ 실제 공격 시나리오
```yaml
# 공격자가 100단계로 구성된 YAML 전송
level0:
  level1:
    level2:
      ... (97단계 더)
        level99:
          data: "payload"

서버: YAML 파싱 시도
↓
Stack 메모리 부족
↓
StackOverflowError
↓
서비스 불가 (DoS)
```

---

## 📌 시드 3: Billion Laughs DoS (엘리아스 폭탄)

**파일:** `seed_billion_laughs_dos.yaml`

```yaml
a: &a
  - a
  - a
  - a
a: &b
  - *a
  - *a
  - *a
c: &c
  - *b
  - *b
  - *b
d: &d
  - *c
  - *c
  - *c
e: &e
  - *d
  - *d
  - *d
f: *e
```

### 🎯 의도
**지수 함수적 메모리 확장** (XML의 Billion Laughs 공격을 YAML로 구현)

### 🔴 취약점: Memory DoS / CPU DoS
```
메모리 증폭:

Level 0: &a = [a, a, a]           (3개 원소)
  ↓
Level 1: &b = [*a, *a, *a]        (3×3 = 9개)
  ↓
Level 2: &c = [*b, *b, *b]        (9×3 = 27개)
  ↓
Level 3: &d = [*c, *c, *c]        (27×3 = 81개)
  ↓
Level 4: &e = [*d, *d, *d]        (81×3 = 243개)
  ↓
Level 5: f = *e                    (243개, 최종 메모리 할당)

최종 메모리 사용량:
3^5 = 243배 증폭!

실제로는 더 깊이 하면:
3^10 = 59,049배
3^15 = 14,348,907배
3^20 = 3,486,784,401배
```

### 🔍 SnakeYAML의 문제점
```java
// Alias 처리 시 각 참조마다 전체 객체 복제
yaml.load(billionLaughsYAML)
↓
f 파싱
  └─ *e 참조 (243개 리스트 로드)
    └─ 메모리에 복제
      └─ 243 × 기타 데이터 = 메모리 폭탄
```

### ⚠️ 실제 공격 사례
```
원래: 1KB YAML 파일
↓
파싱 후: 1GB 이상 메모리 할당
↓
서버의 모든 메모리 고갈
↓
다른 프로세스/서비스도 영향
↓
전체 서버 다운 (DoS)
```

**유명한 예:**
- XML Billion Laughs 공격 (2003)
- SVG ZipBomb (이미지로 위장한 폭탄)
- YAML은 같은 메커니즘 사용 가능

---

## 📌 시드 4: Self-Referencing Alias (자기참조 엘리아스)

**파일:** `seed_self_referencing_alias.yaml`

```yaml
x: &x
  - *x
  - *x
  - *x
  - *x
```

### 🎯 의도
**엘리아스가 자신을 포함하는 리스트 구조**

### 🔴 취약점: Stack Overflow + Memory DoS (복합)
```
구조:
x = &x [*x, *x, *x, *x]
     ↑
     └─ 자신을 포함!

파싱 과정:
x 정의 시도
  └─ x의 값: 리스트
    └─ 첫 번째 원소: *x 참조
      └─ x의 값을 다시 로드 (재귀!)
        └─ 다시 *x 참조...

결과:
- 무한 재귀 (Recursive Anchor와 유사)
- 스택 오버플로우 + 메모리 부족
- 동시에 양쪽 취약점 트리거
```

### 🔍 Serialization 문제
```
YAML 파서가 이 구조를 메모리에 표현하려 시도:
x.value[0] = x         ← x에 x 자신이 포함됨
x.value[1] = x         ← 순환 참조
x.value[2] = x
x.value[3] = x

이를 직렬화하거나 순회하려 하면 무한 루프
```

### ⚠️ 탐지 어려움
일반적인 방어:
```java
// "순환 참조 감지" 로직이 필요
Set<Object> visited = new HashSet<>();
while (parsing) {
  if (visited.contains(current)) {
    throw CyclicReferenceException();
  }
  visited.add(current);
}
```

SnakeYAML 1.30은 이런 방어가 부족함!

---

## 📌 시드 5: Large String Expansion (문자열 확장 폭탄)

**파일:** `seed_large_string_expansion.yaml`

```yaml
str: &str "xxxx...xxxx" (256자)
expanded:
  - *str
  - *str
  - *str
  - *str
  - *str
  - *str
  - *str
  - *str
  - *str
  - *str
```

### 🎯 의도
**큰 문자열을 10번 반복 참조** → 메모리 폭증

### 🔴 취약점: Memory DoS
```
메모리 계산:

문자열 크기: 256 bytes
참조 개수: 10
할당 메모리: 256 × 10 = 2.56 KB

→ 작아 보이지만, Jazzer가 변형하면:
  - 문자열을 1MB로 확장
  - 참조를 100개로 확장
  - 1MB × 100 = 100MB 메모리 폭증!

더 심하면:
  - 1GB 문자열 × 1000개 참조 = 1TB 메모리 요청
```

### 🔍 왜 위험한가?
```
1. YAML이 작아 보임 (파일 크기: 몇 KB)
2. 하지만 파싱 후 메모리는 1000배 이상 증폭
3. 악성 파일을 HTTP로 전송 가능 (네트워크 부담 적음)
4. 서버 메모리 중폭 소비 → 서비스 불가
```

### ⚠️ 실제 공격 벡터
```
HTTP POST /upload
Content-Type: application/yaml
Content-Length: 500  ← 작은 파일처럼 보임

str: &str "A" * 1000000
expanded: [*str] * 1000
```

---

## 📌 시드 6: Map Flood (맵 반복 폭탄)

**파일:** `seed_map_flood.yaml`

```yaml
data: &data
  key1: val1
  key2: val2
  key3: val3
  key4: val4
  key5: val5
  key6: val6
  key7: val7
  key8: val8
  key9: val9
  key10: val10
expanded:
  - *data
  - *data
  - *data
  - *data
  - *data
```

### 🎯 의도
**복합 데이터 구조(맵)를 반복 참조** → CPU + Memory DoS

### 🔴 취약점: Memory + CPU DoS
```
메모리:
- 각 맵: 10개 키-값 쌍
- 참조: 5번
- 할당: 맵당 ~500바이트 × 5 = 2.5KB

CPU:
- 각 참조마다 맵 전체를 해시 테이블로 변환
- 10개 키 × 5참조 = 50번의 HashMap 해싱 작업
- Jazzer가 변형하면 (예: 1000개 키, 100참조)
  → 100,000번의 해싱 = CPU 폭탄
```

### 🔍 실제 처리 과정
```java
// YAML 파서의 맵 처리
for (reference) {
  for (key in mapData) {
    hash(key);           // CPU 사용
    allocate(value);     // 메모리 사용
  }
}
// 결과: 이중 루프 → O(n²) 복잡도!
```

### ⚠️ 가장 은폐하기 쉬운 공격
- 일반적인 YAML 같이 보임
- Denial of Service 의도가 명확하지 않음
- 규칙 기반 필터로 적발 어려움
- 하지만 서버의 CPU를 완전히 독점 가능

---

## 🎯 6개 시드의 관계도

```
┌─ Stack Overflow 그룹
│  ├─ seed_recursive_anchor.yaml      (무한 재귀)
│  ├─ seed_deep_nesting.yaml          (중첩 스택)
│  └─ seed_self_referencing_alias.yaml (복합 재귀)
│
├─ Memory DoS 그룹
│  ├─ seed_billion_laughs_dos.yaml    (지수 확장)
│  └─ seed_large_string_expansion.yaml(문자열 폭탄)
│
└─ CPU DoS 그룹
   └─ seed_map_flood.yaml             (해싱 폭탄)
```

---

## 📊 취약점 심각도 비교

| 시드 | 취약점 타입 | 영향 | 감지 난이도 | 심각도 |
|------|----------|------|-----------|--------|
| Recursive Anchor | Stack Overflow | 프로세스 크래시 | 쉬움 | 🔴 심각 |
| Deep Nesting | Stack Overflow | 프로세스 크래시 | 쉬움 | 🔴 심각 |
| Billion Laughs | Memory DoS | 메모리 고갈 | 중간 | 🔴 심각 |
| Self-Referencing | Stack + Memory | 복합 크래시 | 어려움 | 🔴🔴 매우위험 |
| String Expansion | Memory DoS | 메모리 고갈 | 어려움 | 🟠 높음 |
| Map Flood | CPU DoS | 응답 지연/불가 | 어려움 | 🟠 높음 |

---

## 🛡️ 방어 방법

### SnakeYAML 1.31+ 또는 대체 라이브러리
```java
// 1. 순환 참조 감지
Set<String> anchors = new HashSet<>();

// 2. 깊이 제한
int maxDepth = 50;

// 3. 메모리 할당량 제한
long maxMemory = 100 * 1024 * 1024; // 100MB

// 4. 파싱 시간 제한
timeout = 5000; // 5초
```

### YAML 입력 검증
```yaml
# ❌ 금지할 패턴
1. &를 포함한 앵커 사용 최소화
2. 깊이 20 이상의 중첩 제한
3. 과도한 반복 참조 제한
```

---

## 🔬 Jazzer로 퍼징할 때

이 6개 시드가 도움이 되는 이유:

```
1️⃣ Jazzer가 각 시드에서 시작
2️⃣ 약간씩 변형 (깊이 증가, 참조 추가 등)
3️⃣ 파싱 시도
4️⃣ 크래시 감지
5️⃣ 원인 분석 → 취약점 증명

결과: SnakeYAML 1.30의 취약점을 체계적으로 발현
```

**Fuzzing 시나리오:**
- Seed: `seed_recursive_anchor.yaml` (34 bytes)
- 변형 후: `recursive_anchor_modified.yaml` (50 bytes)
- 파싱 시도: `yaml.load(modified)`
- 결과: StackOverflowError 또는 새로운 크래시 발견

---

## 📌 결론

이 6개 시드는 **SnakeYAML 1.30의 알려진 Deserialization 취약점**들을 모두 커버합니다:

✅ **CVE-2013-0156 스타일** (Ruby on Rails YAML DoS)  
✅ **XML Billion Laughs의 YAML 변형**  
✅ **자기 참조 순환 구조 (Cyclic References)**  
✅ **깊은 중첩으로 인한 Stack Overflow**  

Jazzer가 이들을 변형하고 테스트하면서:
- 더 깊은 취약점 발견
- 새로운 공격 벡터 탐색
- JaCoCo로 영향 범위 측정
- 미래 버그 패턴 학습
