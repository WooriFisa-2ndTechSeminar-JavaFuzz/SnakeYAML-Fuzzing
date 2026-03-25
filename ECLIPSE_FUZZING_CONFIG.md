# Eclipse Run Configuration - SnakeYAML 퍼징 설정

## 🎯 목표
분리된 seed와 corpus를 사용하여 SnakeYAML 1.30 취약점 퍼징

---

## 📂 디렉터리 구조 확인

```
프로젝트루트/
├── seed/                             ← 사용자 정의 시드 (Git 추적)
│   ├── seed_recursive_anchor.yaml
│   ├── seed_deep_nesting.yaml
│   ├── seed_billion_laughs_dos.yaml
│   ├── seed_self_referencing_alias.yaml
│   ├── seed_large_string_expansion.yaml
│   └── seed_map_flood.yaml
│
├── corpus/                           ← Jazzer 자동 생성 (무시)
│   └── (자동 생성 파일들, .gitignore 적용)
│
├── crash/                            ← 크래시 재현 파일
│   └── Crash_ASAN_SEGV_xxxxx
│
└── .gitignore                        ← 자동 생성
```

---

## ⚙️ Eclipse Run Configuration 설정

### Step 1: Run → Run Configurations 열기

### Step 2: Main 탭

| 항목 | 값 |
|------|-----|
| Project | SnakeYAMLFuzzing |
| Base directory | ${project_loc} |

### Step 3: Arguments 탭

**Program arguments:**
```
clean test -Djazzer.duration=600s -Djazzer.seed_corpus_dir=seed -Djazzer.corpus_dir=corpus -Djazzer.reproducer_path=crash
```

**VM arguments:**
```
-Xmx2g -Xss10m
```

### Step 4: Environment 탭

다음 환경변수들을 추가하세요:

| 변수명 | 값 |
|--------|-----|
| `JAZZER_FUZZ` | `1` |
| `JAZZER_FUZZ_TEST` | `fuzzing.SnakeYamlFuzzTest::fuzzYamlLoad` |
| `JAZZER_SEED_CORPUS_DIR` | `seed` |
| `JAZZER_CORPUS_DIR` | `corpus` |
| `JAZZER_KEEP_GOING` | `1` |
| `JAZZER_REPRODUCER_PATH` | `crash` |

### Step 5: Apply → Run

---

## 📊 실행 시나리오

### A) 빠른 커버리지 테스트 (5분)
```
Duration: 300s
목표: 기본 커버리지 확인
```

### B) 표준 퍼징 (10분, 권장)
```
Duration: 600s
목표: 취약점 발견 + 커버리지 증가
```

### C) 심화 분석 (20분)
```
Duration: 1200s
목표: 새로운 취약점 탐색
```

---

## 🔍 실행 중 모니터링

**Console에서 주시할 신호:**

### ✅ 정상 실행
```
[INFO] Running fuzzing.SnakeYamlFuzzTest.fuzzYamlLoad
INFO: Corpus size: 6 entries, libFuzzer is processing the seed corpus.
INFO: loaded 6 inputs from the seed corpus
INFO: -artifact_prefix is not set. Using 'artifact-' as default.
```

### 🚨 크래시 발견
```
==12345==ERROR: AddressSanitizer:SEGV on unknown address 0x...
(T=123 ms, U=456 units, V=789 units, PC=0x...)

SUMMARY: AddressSanitizer: SEGV in ...
ERROR: Crash detected!
```

---

## 📁 실행 후 확인 사항

### 1️⃣ 시드 디렉터리 (변경 없음)
```bash
ls -la seed/
```
→ 6개 파일, 변경 없음 ✓

### 2️⃣ 코퍼스 디렉터리 (자동 추가됨)
```bash
ls -la corpus/ | wc -l
```
→ 초기 ~30개 → 추가 증가 (변경됨) ✓

### 3️⃣ 크래시 발견 시
```bash
ls -lh crash/
```
→ Crash_ASAN_SEGV_xxxxx 파일 존재 ✓

---

## 🎯 시드별 취약점 매핑

| 시드 파일 | 취약점 | 예상 결과 |
|-----------|--------|---------|
| seed_recursive_anchor.yaml | 자기참조 anchor | StackOverflow |
| seed_deep_nesting.yaml | 깊은 구조 | StackOverflow |
| seed_billion_laughs_dos.yaml | Alias 폭탄 | Memory DoS |
| seed_self_referencing_alias.yaml | 자기참조 alias | StackOverflow |
| seed_large_string_expansion.yaml | 문자열 확장 | Memory DoS |
| seed_map_flood.yaml | 맵 반복 | CPU DoS |

---

## ✅ 체크리스트

- [ ] seed 디렉토리에 6개 시드 파일 생성됨
- [ ] Eclipse Run Configurations Arguments 탭 수정됨
- [ ] Eclipse Run Configurations Environment 탭 수정됨
- [ ] .gitignore 파일 생성됨
- [ ] Run 버튼 클릭하여 퍼징 시작
- [ ] Console에서 "Corpus size: 6 entries" 확인
- [ ] 크래시 발견 대기
- [ ] crash 폴더 확인

---

## 🚀 실행 명령어 (참고용)

```bash
cd /home/sajaeheon/Dev/projects/Fuzzing-Tech-Seminar/SnakeYAMLFuzzing

# 설정 확인
echo "=== Seeds ==="
ls -la seed/

echo "=== Corpus ==="
find corpus/ -type f | wc -l

echo "=== Reproducers ==="
ls -la crash/ 2>/dev/null || echo "아직 없음 (크래시 발견 후 생성)"
```
