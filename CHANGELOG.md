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
