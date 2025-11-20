<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# PR-agent Changelog

## [0.0.1]
### Added
- AI Assistant - Automatically generates concise PR descriptions based on your code changes
- Markdown Preview - Real-time preview of your PR description with full markdown support (requires Markdown plugin)
- Mermaid Diagram Generation - Automatically generates class diagrams and sequence diagrams to visualize your changes
- Interactive Modification - Refine your PR description through conversational text input
- Configurable Base Prompt - Customize the base prompt to tailor PR descriptions to your team's style and requirements

## [0.0.2]
### Added
- Multi-Provider LLM Support - Users can now select from 7 LLM providers (Anthropic, OpenAI, Google, DeepSeek, OpenRouter, Ollama, Bedrock) with 100+ model mappings
- Git Status Monitoring - Automatic detection of git repository changes (commits, checkouts, branch switches) with real-time PR notes panel refresh

### Changed
- AgentService now retrieves user-selected model/provider from storage instead of hardcoding Sonnet_4
- PR notes panel now automatically refreshes when git operations occur, eliminating manual refresh needs
- Text area line wrapping - Improved readability for long text content


## [0.0.3]
### Added
- Error Logging System - Comprehensive error logging infrastructure with persistent XML storage (max 100 entries, FIFO)
- Debug Panel - New Debug tab in tool window for browsing error logs with Previous/Next navigation and clipboard support

### Changed
- Proactive Preview Messaging - Preview panel now initializes on load to immediately inform users about markdown plugin or JCEF unavailability

### Fixed
- Multi-Provider Execution - Fixed critical issue where non-Anthropic LLM providers (OpenAI, Google, Ollama) failed to execute prompts due to hardcoded Anthropic executor

## [0.0.4]
### Added
- Config Panel - Add include pull_request_template.md check box to let the output to match the project's PR template format
- PR Editor Panel - Add PR title generation
- Set current github user as the assignee of the PR

### Fixed
- Deprecated API usage - fix it by remove unused class
