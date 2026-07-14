# Babi Agent 🌏

Babi Agent is an intelligent agent application built with Spring AI Alibaba.

## Features

- 🤖 Powered by Spring AI Alibaba and DashScope (Qwen) models
- 🔧 Built-in tools for date/time retrieval and text processing
- 💬 RESTful API for agent interaction
- 🎨 Integrated with Spring AI Alibaba Studio for visual debugging
- 🧠 Supports multi-turn conversations with thread isolation

## Quick Start

1. Set your DashScope API key:
   ```bash
   export DASHSCOPE_API_KEY=your_api_key_here
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run -pl babi-agent
   ```

3. Access the Chat UI at `http://localhost:8082/chatui/index.html`

&copy; [hongxi.org](http://hongxi.org)
