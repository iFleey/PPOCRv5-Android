# Contributing to PPOCRv5-Android

Thank you for your interest in contributing to this project.

By contributing to this project, you agree to abide by the terms specified in the [Code of Conduct](CODE_OF_CONDUCT.md).

## Requirements

Before proposing a change, please open an issue and discuss it with the maintainers.

## How to Contribute

1. Fork the repository
2. Clone the forked repo to your local machine
3. Open the project in your IDE
4. Make your changes
5. Format your code (see below)
6. Create a pull request with a meaningful title and description

## Report Issues

Report issues and request features in the [Issues](https://github.com/iFleey/PPOCRv5-Android/issues) section.

## Source Code Format

This project uses `.editorconfig` for consistent code formatting. JetBrains IDEs (Android Studio, IntelliJ IDEA) will automatically follow these rules.

### Formatting Rules

| File Type         | Indent | Max Line Length |
|-------------------|--------|-----------------|
| Kotlin (*.kt/kts) | 2      | 120             |
| C/C++ (*.cpp/h)   | 4      | 120             |
| XML/JSON/YAML     | 2      | 120             |
| CMake             | 4      | 120             |

### Creating the Reformat Scope

The `.idea` directory is excluded by `.gitignore`, so you need to create the `Reformat` scope manually:

1. Open `Settings` -> `Appearance & Behavior` -> `Scopes`
2. Click `+` to add a new `Shared` scope
3. Name it `Reformat`
4. Set the pattern to:

```
(file[PPOCRv5-Android*]:*//*)&&!file[PPOCRv5-Android.app.main]:cpp/litert_cc_sdk//*
```

This pattern includes all project files but excludes the `litert_cc_sdk` directory (third-party code).

### Before Committing

Run the code formatter in JetBrains IDE:

1. Right-click on the `Project` root directory
2. Select `Reformat Code`
3. In the dialog, enable:
   - Optimize imports
   - Rearrange entries
4. Click `Filters` -> `Scope` -> select `Reformat` (the scope you created above)
5. Click `OK`
6. Run the formatter

This ensures all code follows the project's formatting conventions before submission.

## Contribution and Copyright

### License Agreement

By submitting code to this project (including Pull Requests), you agree that your contributions will be licensed under the Apache License 2.0.

### Attribution Guidelines

To maintain a clean codebase and respect intellectual property, please follow these guidelines for copyright headers:

#### Modifying Existing Files

If the file header lists three or fewer authors (including yourself):

Add your name to the Copyright line.

```
// Copyright (C) 2025 Fleey, [Existing Author], [Your Name]
```

If adding your name would exceed three authors:

Do not list a long name list. Generalize the author list instead.

```
// Copyright (C) 2025 Fleey, [Author A], [Author B], and contributors
```

#### Creating New Files

For completely original code:

Add your name with the project's standard license header.

```
// Copyright (C) 2025 [Your Name]
```

For files containing third-party or copyrighted content:

- Do not remove or modify the original copyright notice and license header
- Keep the original author information intact
- Do not add your name to avoid legal and copyright disputes
- You may add "Modified by [Your Name]" below the original notice to indicate your modifications, but never overwrite the original author's copyright

#### Documentation and Other Changes

For documentation, configuration files, or simple typo fixes, do not add attribution in the file. Your contributions will be recorded in the Git commit history.
