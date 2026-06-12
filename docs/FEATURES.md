# LeanType Features Guide

LeanType integrates with AI providers to offer advanced proofreading and translation capabilities directly within the keyboard. This guide explains how to set up the supported providers.

## Index

| Section | Description |
| :--- | :--- |
| 🆕 **[Summary of New Features](#summary-of-new-features)** | Quick overview of what's new. |
| 🤖 **[Supported AI Providers](#supported-ai-providers)** | Start here to choose your AI backend. |
| ⚡ **[Groq](#1-groq)** | Ultra-fast cloud inference. |
| 🌟 **[Google Gemini](#2-google-gemini)** | Reliable general-purpose AI. |
| ⚙️ **[HF/OpenAI-compatible](#3-hfopenai-compatible-generic-provider)** | Use Mistral, DeepSeek, OpenRouter, etc. |
| 🧠 **[Custom AI Keys](#4-custom-ai-keys--keywords)** | Configure custom prompts and personas. |
| 🛡️ **[Offline Proofreading](#5-offline-proofreading-privacy-focused)** | Privacy-first, on-device AI. |
| 📝 **[Text Expander](#6-text-expander)** | Custom text shortcut expansion. |
| 🖱️ **[Touchpad Mode](#7-touchpad-mode)** | Full-screen touchpad gestures and controls. |

## Summary of New Features

| Feature | Function | Settings Location |
| :--- | :--- | :--- |
| **Multi-Provider AI** | Uses Gemini, Groq, or OpenAI to proofread/rewrite text. Fetch latest models dynamically. | `AI Integration > Set AI Provider` |
| **Offline Proofreading** | Private, on-device AI for grammar (requires downloads). | `AI Integration > Offline Proofreading` |
| **Custom AI Keys** | 10 toolbar keys with custom prompts, tags (themed capsules), and toggle settings (supports hashtags). | `AI Integration > Custom Keys` |
| **AI Translation** | Translates selected text via your configured AI provider (includes separate model selector). | Toolbar > Translate Icon |
| **Floating Keyboard** | Detach the keyboard into a draggable window with a persistent mode option. | Toolbar > Floating Keyboard |
| **Touchpad Mode** | Swipe up on Spacebar to control cursor, including full-screen laptop-style touchpad. | `Gesture typing > Vertical spacebar swipe` |
| **Split Suggestions** | Separates suggestions from toolbar for quicker access. | `Appearance > Split toolbar & suggestions` |
| **Build Variants** | Choose Standard, Offline, or Offline Lite versions. | GitHub Releases |
| **Clear Incognito** | Shows a clear "Hat & Glasses" icon when Incognito is active. | *Automatic (when Incognito)* |
| **Clipboard Search** | Search history, undo swipe-delete, and optionally fold pinned items by default. | Clipboard Toolbar > Search Icon |
| **Dictionary Import** | Import personal words from Google Gboard/other keyboards. | `Text correction > Dictionary > Import` |
| **Force Auto-Caps** | Toggle to ensure automatic capitalization works reliably. | `Text correction > Auto-capitalization` |
| **Emoji Search** | Search for emojis by name. | `Emoji Key > Search Icon` |
| **Screenshot Suggestion** | Suggests recently taken screenshots for quick sharing. | `Text correction > Suggest recent screenshots` |
| **Screenshot on Clipboard** | Automatically saves taken screenshots to your clipboard history. | *Automatic (when enabled)* |
| **Clipboard Undo** | Undo swipe-to-delete on clipboard items with a timed undo bar. | *Automatic (on swipe delete)* |
| **Text Expander** | Expand custom shortcuts using dynamic template variables (date, time, clipboard, custom placeholders). | `Text correction > Text Expander` |

---

## Emoji Search

*   **Functionality**: Search for emojis by keyword/name directly from the emoji palette.
*   **Requirement**: You must load an **Emoji Main Dictionary**.
*   **How to Setup**:
    1.  Go to **Settings > Text correction > Dictionary**.
    2.  Tap on your language (e.g., **English**).
    3.  Ensure the **Emoji English** (or relevant emoji dict) is available/downloaded.
    4.  *Note*: Basic emoji search requires this dictionary to map words to emoji characters.

---

## Screenshot Suggestion

*   **Functionality**: When you take a screenshot, LeanType detects it and shows a suggestion in the suggestion strip for quick sharing. Tapping the suggestion inserts the screenshot as an image into the current input field (if the app supports image insertion).
*   **Permissions**: Requires **storage/media access** (`READ_MEDIA_IMAGES` on Android 13+, or `READ_EXTERNAL_STORAGE` on older versions). The keyboard will prompt for permission when this feature is first enabled.
*   **How to Enable** (Disabled by default):
    1.  Go to **Settings > Text correction**.
    2.  Enable **Suggest recent screenshots**.
*   **Behavior**:
    *   Screenshots taken within the last **4 minutes** are detected.
    *   A thumbnail preview is shown in the suggestion strip.
    *   Tapping the suggestion inserts the image; tapping the close button dismisses it.

---

## Screenshot on Clipboard

*   **Functionality**: Taken screenshots are automatically saved to your clipboard history for later use.
*   **Requirement**: Clipboard history must be enabled.
*   **How to Enable**: Ensure **Suggest recent screenshots** is enabled in Text correction.

---

## Clipboard Item Delete Undo

*   **Functionality**: When you swipe to delete an item from your clipboard history, a brief undo bar appears, allowing you to restore the deleted item.
*   **Behavior**: Provides a safety net against accidental deletions of important clipboard content.

---

## Supported AI Providers

| Provider | Privacy | Setup | Free Tier | Best For |
| :--- | :--- | :--- | :--- | :--- |
| **Groq** | 🟡 Average | 🟢 Easy | High | **Speed** |
| **Google Gemini** | 🔴 Low | 🟢 Easy | Generous | General Purpose |
| **HF/OpenAI-compatible** | ⚙️ *Varies* | 🟡 Medium | *Varies* | **Fully Customizable** |
| **Offline (Llama)** | 🟢 **Best** | 🟡 Medium | ∞ Unlimited | **Privacy** |

> [!TIP]
> The **HF/OpenAI-compatible** option is fully customizable—you can change the API endpoint, token, and model to use *any* OpenAI-compatible service (OpenRouter, Mistral, DeepSeek, HuggingFace, etc.).

**Privacy Links:**
- [Groq Privacy Policy](https://groq.com/privacy-policy)
- [Google Gemini API Terms](https://ai.google.dev/gemini-api/terms)
- [OpenRouter Privacy](https://openrouter.ai/privacy)
- [HuggingFace Privacy](https://huggingface.co/privacy)

> [!IMPORTANT]
> **Sensitive Data**: **Do not process sensitive information** (passwords, credit card numbers, private addresses) using the AI Proofreading or Translation features.
>
> **Offline Version Guarantee**: The **Offline Version** physically excludes all network code at build time. It is impossible for it to connect to the internet, making it safe for all data.

---

## 1. Groq

Groq is a cloud API provider that uses custom LPUs (Language Processing Units) to deliver **extremely fast** inference speeds, making it feel almost instant compared to other cloud providers.

### Setup
1.  **Get API Key**: Visit [Groq Console](https://console.groq.com/keys) and create a key (starts with `gsk_`).
2.  **Configure in LeanType**:
    *   **Provider**: Select **Groq**.
    *   **API Token**: Paste your Groq API Key.

### Available Models
| Model ID | Context | Description |
| :--- | :--- | :--- |
| `llama-3.3-70b-versatile` | 128k | **Best Overall**. High intelligence. |
| `llama-3.1-8b-instant` | 128k | **Fastest**. Quick grammar fixes. |
| `meta-llama/llama-4-scout-17b-16e-instruct` | 128k | New scout model. Good balance. |
| `meta-llama/llama-4-maverick-17b-128e-instruct`| 128k | New maverick model. Better reasoning. |
| `qwen/qwen3-32b` | 128k | Good speed and logic. |
| `openai/gpt-oss-120b` | 128k | Large OSS GPT model. |
| `openai/gpt-oss-20b`  | 128k | Fast OSS GPT model. |
| `groq/compound`| 128k | Deep-thinking internal model. |
| `groq/compound-mini` | 128k | Optimized internal model. |
| `moonshotai/kimi-k2-instruct` | 128k | Lightweight reasoning model. |
| `moonshotai/kimi-k2-instruct-0905` | 128k | Specialized kimi instruction model. |
| `canopylabs/orpheus-v1-english` | 128k | CanopyLabs English priority model. |
| `canopylabs/orpheus-arabic-saudi` | 128k | CanopyLabs Arabic dialect model. |
| `allam-2-7b` | 128k | Efficient 7B general use model. |

---

## 2. Google Gemini

### Get an API Key
1.  Go to [Google AI Studio](https://aistudio.google.com/app/apikey).
2.  Click **Create API Key**.
3.  Copy the key.

### Configuration
1.  Go to **Settings > AI Integration > Gemini API Key**.
2.  Paste your API key.
3.  Select a model.

### Available Models
| Model ID | Description |
| :--- | :--- |
| `gemini-2.5-flash` | **Fastest**. Great for quick tasks. |
| `gemini-2.5-pro` | High intelligence. Best overall quality. |
| `gemini-flash-latest` | Fast and capable (latest flash). |
| `gemini-flash-lite-latest` | Lightweight flash variant. |
| `gemini-pro-latest` | Latest pro model. |
| `gemini-3.1-pro-preview` | Next-gen pro preview. |
| `gemini-3.1-pro-preview-customtools` | Pro preview with custom tools support. |
| `gemini-3-pro-preview` | Gemini 3 pro preview. |
| `gemini-3-flash-preview` | Gemini 3 flash preview. |
| `deep-research-pro-preview-12-2025` | Deep research specialized model. |
| `gemma-3-27b-it` | Large Gemma model. High quality. |
| `gemma-3-12b-it` | Mid-size Gemma model. Good balance. |
| `gemma-3-4b-it` | Compact Gemma model. Fast. |
| `gemma-3-1b-it` | Smallest Gemma model. Ultra-light. |
| `gemma-3n-e4b-it` | Efficient Gemma variant (4B). |
| `gemma-3n-e2b-it` | Efficient Gemma variant (2B). |

---

## 3. HF/OpenAI-compatible (Generic Provider)

This provider supports any service using the standard OpenAI Chat Completion API format.

### A. HuggingFace Inference API

#### Setup
1.  **Get Token**: Go to [HuggingFace Settings](https://huggingface.co/settings/tokens) and create a 'Read' token.
    *   *Note*: HuggingFace may require you to add a valid payment method to your account to "unlock" the Inference API, even for the free tier (to prevent abuse).
2.  **Configure in LeanType**:
    *   **Provider**: Select **HF/OpenAI-compatible**.
    *   **API Token**: Paste your HF Access Token.
    *   **API Endpoint**: `https://api-inference.huggingface.co/models/<USER>/<MODEL>/v1/chat/completions`
    *   **Model Name**: `<USER>/<MODEL>` (e.g., `meta-llama/Meta-Llama-3-8B-Instruct`).

### B. OpenRouter / Other Providers

1.  **API Endpoint**: Enter the provider's completion URL.
    *   *OpenRouter*: `https://openrouter.ai/api/v1/chat/completions`
    *   *DeepSeek*: `https://api.deepseek.com/chat/completions`
    *   *Mistral*: `https://api.mistral.ai/v1/chat/completions`
    *   *OpenAI*: `https://api.openai.com/v1/chat/completions` (Default)
2.  **API Token**: Enter your API Key from that provider.
3.  **Model Name**: Enter the exact model ID from the provider's documentation (e.g., `deepseek-chat`, `mistral-large-latest`, `gpt-4o-mini`).

---

## 4. Custom AI Keys & Keywords

You can assign custom prompts to 10 specific keys in the toolbar. These keys can "act" differently based on the keywords (hashtags) you include in your prompt.

### How to Use
1.  Go to **Settings > AI Integration > Custom Keys**.
2.  Tap a key (1-10) to configure it.
3.  Enter your instructions. You can use the following hashtags to control the AI's behavior and output format.

### Custom Text Labels / Tags
You can assign custom text labels (up to 12 characters) to toolbar keys instead of displaying generic AI icons:
1. Fill in the **Tag / Label** field when configuring a custom AI key (e.g. `French`, `Rewrite`, `Reply`).
2. Toggle **Show tags on keyboard** under **Settings > AI Integration > Custom Keys**.
3. Custom labels will display as sleek, themed capsules directly on the keyboard toolbar.

### AI Persona Keywords
Add these to your prompt to enforce a specific role.

| Keyword | Persona / Behavior | System Instruction Added |
| :--- | :--- | :--- |
| `#editor` | **Text Editor** | "Output ONLY the edited text. Do not add any conversational filler." |
| `#outputonly` | **Strict Output** | "Output ONLY the result. Do not add introductions or explanations." |
| `#proofread` | **Proofreader** | "Fix grammar and spelling errors. Output ONLY the fixed text." |
| `#paraphrase` | **Rewriter** | "Rewrite using different words while keeping the meaning." |
| `#summarize` | **Summarizer** | "Provide a concise summary." |
| `#expand` | **Writer** | "Expand on the text with more details." |
| `#toneshift` | **Tone Adjuster** | "Adjust the tone as requested." |
| `#generate` | **Content Generator** | "You are a creative content generator. Output ONLY content." |

### Input Handling Keywords
Control how the result is inserted.

| Keyword | Behavior | Use Case |
| :--- | :--- | :--- |
| **(Default)** | **Replace**: The AI output replaces the selected text or the entire text field content. | Proofreading, rewriting, summarizing. |
| `#append` | **Append**: The AI output is added to the **end** of the text field (or selection) instead of replacing it. | Generating replies, continuing a story, adding a sign-off. |
| `#showthought` | **Show Thinking**: Preserves the "thinking" process (e.g., `<think>...</think>`) from reasoning models like Qwen. | Debugging reasoning, seeing the AI's thought process. |

### Examples

**1. Standard Proofreading (Replace)**
> Prompt: `Fix grammar #proofread`
> *Result: Replaces your text with the corrected version.*

**2. Generate a Reply (Append)**
> Prompt: `Generate a polite decline to this invitation #generate #append`
> *Result: Keeps the original invitation text and adds your polite decline at the end.*

**3. Strict Rewriting**
> Prompt: `Rewrite this to be more professional #editor`
> *Result: Replaces text with professional version, guaranteeing no "Here is the text:" prefixes.*

**4. Tone Shift**
> Prompt: `Make this sound more enthusiastic #toneshift`
> *Result: Rewrites the text with high energy and exclamation points, keeping the core message.*

> [!TIP]
> **Hashtags are Optional**: You can write purely custom prompts (e.g., "Translate to French"). However, without a hashtag like `#outputonly` or `#editor`, the AI might act like a chatbot (e.g., responding with "Sure! Here is the translation: ..."). Using these keywords automatically injects strict system instructions to ensure you get *only* the result you want.

---

## Privacy
*   **Data**: Text is sent directly from your device to the chosen API provider. No intermediate servers are used.

## 5. Offline Proofreading (Privacy Focused)

**Note**: This feature is only available in the "Offline" build flavor of LeanType.

Offline proofreading runs entirely on your device using the `llama.cpp` runtime. No data leaves your device.

> [!NOTE]
> **Status: Beta / Experimental**
> Running large language models on device requires a modern smartphone with sufficient RAM (typically 6GB+). We recommend using highly quantized, compact GGUF models (e.g. Q4_K_M or IQ4_NL) for the best balance of speed, accuracy, and memory usage.

### Setup Instructions

1.  **Download a GGUF Model**: Download a compatible `.gguf` model file (see Recommended Models below).
2.  **Configure App**:
    *   Go to **Settings > Advanced**.
    *   **GGUF Model**: Select the downloaded `.gguf` model file.
    *   **System Instruction**: (Optional) Customize the prompt used to guide the model when proofreading text.
    *   **Translate Instruction**: (Optional) Customize the prompt used for translation.
    *   **Target Language**: Select the target language for offline translation.
    *   **Sampling Settings**: Adjust temperature, Top-K, and Top-P to control model creativity.

### Recommended Models

*   **Llama 3.2 1B Instruct (Q4_K_M)**: Excellent general purpose compact model (~900 MB).
*   **Qwen 2.5 1.5B Instruct (Q4_K_M)**: High accuracy and quality, fast on modern devices (~1.1 GB).
*   **Qwen 2.5 0.5B Instruct (Q4_K_M)**: Extremely lightweight, very fast with minimal memory footprint (~350 MB).

You can find and download these models in GGUF format on HuggingFace (e.g., from users like `bartowski` or `Qwen`).

---

## 6. Text Expander

Text Expander allows you to define custom shortcuts (abbreviations) that automatically expand into longer, structured text templates as you type.

### Key Features
*   **Custom Shortcuts**: Create abbreviations (e.g., `adr`) that expand immediately into complex blocks (e.g., your full postal address).
*   **Dynamic Template Variables**: Enrich your expansions using dynamic variables:
    *   `%date%` - Inserts the current local date.
    *   `%time%` - Inserts the current local time.
    *   `%clipboard%` - Appends the most recently copied text from your clipboard.
    *   `%cursor%` - Positions the typing cursor here after expansion.
    *   `%greeting%` - Inserts "Good morning", "Good afternoon", or "Good evening" depending on the hour.
    *   `%tomorrow%` - Inserts tomorrow's date (YYYY-MM-DD).
    *   `%bullets%` - Inserts a bullet list template (supports count suffix e.g. `%bullets_5%`).
    *   `%list%` - Inserts a numbered list template (supports count suffix e.g. `%list_5%`).
    *   **Custom Placeholders**: Create dynamic input fields (e.g., `%name%`) that prompt you to type a value during the expansion flow.

### Configuration
1.  Navigate to **Settings > Text correction > Text Expander**.
2.  Tap the **+** (Add) button to create a new expansion rule.
3.  Specify the **Shortcut** trigger and the **Expansion** template.
4.  Include dynamic template variables in the template block.

---

## 7. Touchpad Mode

Touchpad Mode replaces the keyboard with a laptop-style touchpad overlay to control the cursor and edit text using fluid gestures.

### How to Enable
*   **Swipe gesture**: Swipe up on the **Spacebar** to temporarily toggle Touchpad Mode.
*   **Toolbar shortcut**: Tap the **Touchpad** icon in the toolbar for a persistent touchpad overlay.

### Touchpad Gestures

#### 1 Finger (Navigation & Selection)
*   **Drag**: Moves the cursor precisely character-by-character.
*   **Double Tap**: Selects the word under the cursor.
*   **Long Press & Drag**: Enters text selection mode and selects text as you drag.

#### 2 Fingers (Navigation, Clipboard, History & Deletion)
*   **Drag Left/Right**: Moves the cursor horizontally word-by-word.
*   **Swipe Up**: Undo.
*   **Swipe Down**: Redo.
*   **Tap**: Inserts a space character.
*   **Double Tap**: Copies selected text (or Pastes clipboard contents if no selection exists).
*   **Triple Tap**: Cuts selected text (or Selects All if no selection exists).
*   **Press & Hold (Long Press)**: Deletes (backspaces) selection / word to the left. Repeats automatically if held.
