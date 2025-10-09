# PR-agent

![Build](https://github.com/hungyanbin/intellij-plugin-PR-agent/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)


<!-- Plugin description -->
AI-powered assistant for generating concise and comprehensive pull request descriptions.

### Features
- **AI Assistant** - Automatically generates concise PR descriptions based on your code changes
- **Markdown Preview** - Real-time preview of your PR description with full markdown support (requires Markdown plugin)
- **Mermaid Diagram Generation** - Automatically generates class diagrams and sequence diagrams to visualize your changes
- **Interactive Modification** - Refine your PR description through conversational text input
- **Configurable Base Prompt** - Customize the base prompt to tailor PR descriptions to your team's style and requirements

### Requirements
- For markdown preview in Android Studio: Requires JBR with JCEF support
- Mermaid plugin (optional) - Required to render diagrams in preview
<!-- Plugin description end -->

## Privacy & Security

### Data Privacy
- **No Data Collection** - This plugin does not collect, store, or transmit any API tokens or keys from users
- **Local Storage Only** - All configuration and settings are stored locally on your machine
- Your API credentials remain secure within your IDE's secure storage

### Git Operations
This plugin executes the following safe git commands:
- **List branches** - To display available branches for comparison
- **Check status** - To understand current repository state
- **Pull changes** - To update branches with latest changes
- **Push changes** - Normal push operations only

**What we DON'T do:**
- We will NOT add extra files to your commits
- We will NOT execute force push (`--force`) on your remote branches
- We will NOT modify your git history or perform destructive operations


