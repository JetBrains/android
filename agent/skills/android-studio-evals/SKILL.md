---
name: write-evals
description: Expertise in building new AI agent evaluations (evals) for Android Studio. Use when creating new test scenarios, defining agent prompts, or adding verification logic for agent-based features.
---

# Building Studio Agent Evaluations

You are a specialist in authoring Android Studio Agent evaluations. Your goal is to design scenarios that effectively measure an agent's ability to solve specific IDE tasks.

## 1. Development Workflow

### Step 1: Scenario Setup (Arrange)
Choose the appropriate project setup strategy based on your evaluation needs:

**A. Lightweight In-Memory Projects** (Preferred for speed & simplicity)
- Use `@get:Rule val androidProjectRule = AndroidProjectRule.inMemory()`.
- Use `androidProjectRule.fixture.addFileToProject` to create individual source files the agent will modify.

**B. Real Gradle Projects from Prebuilts** (For complex build/sync behaviors)
- Use `AndroidGradleProjectRule` to load a complete, pre-configured project.
- Store the project as a `src.zip` file in the `prebuilts/studio/evalprojects/` directory.
- Use or create a composite rule (like `UiToolsEvalRule`) that extracts the zip and prepares the `AgentEvalRule`.

### Step 2: Agent Execution (Act)
- **Configuration**: Use `fixture.configureAgent` to specify the model and the tools the agent needs.
- **Prompting**: Call `run(prompt)` with a clearly defined task using `buildPrompt`.

### Step 3: Verification (Assert & Judge)
- **Functional Assertions**: Use `fixture.assertTrue` to verify binary outcomes (e.g., "Project must compile after the fix").
- **Qualitative Judgment**: Use `fixture.judge(prompt)` for human-like assessment. Provide the judge with specific criteria.
- **Golden Diffs**: Use `judgeGoldenDiff` when you have a known-good reference solution to compare against.

## 2. Integrating into the Build System

- **Using Macros**: Prefer existing project-specific macros (e.g., `transform_ui_eval`) found in local `.bzl` files. These handle infrastructure (RBE, memory, networking) and pass the `-Deval.project_path` automatically.
- **Prebuilt Dependencies**: If using a prebuilt project, ensure your `BUILD.bazel` target depends on the `//prebuilts/studio/evalprojects/...:target` containing your `src.zip`.

## 3. Execution Commands

### Remote (Standard)
Run the evaluation in the remote environment with secure key management:
```bash
bazel test --test_env="SECRET_MANAGER_KEY=DEFAULT_GEMINI_KEY" --config=remote //path/to/eval:target_name
```

### Local (Debug)
```bash
bazel test --test_env=GOOGLE_API_KEY=$MY_KEY //path/to/eval:target_name
```

## Checklists
- [ ] **Scenario**: Did you choose the right project setup (In-Memory vs. Prebuilt)?
- [ ] **Prebuilts**: If using prebuilts, is the project zipped as `src.zip` and added to the correct `BUILD` file in the prebuilts repo?
- [ ] **Judgment**: Is the LLM Judge prompt specific enough to distinguish between a pass and a partial pass?
