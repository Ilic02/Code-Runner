import './style.css'

// Umesto glavnog ulaza ('monaco-editor', koji uvlači ~50 jezika i teške TypeScript/CSS/HTML/JSON
// module) uvozimo jezgro, nekoliko funkcija editora i samo C/C++ i Javu.
import * as monaco from 'monaco-editor/editor/editor.api'
import editorWorker from 'monaco-editor/editor/editor.worker?worker'

import 'monaco-editor/editor/browser/coreCommands'
import 'monaco-editor/editor/browser/widget/codeEditor/codeEditorWidget'
import 'monaco-editor/editor/contrib/bracketMatching/browser/bracketMatching'
import 'monaco-editor/editor/contrib/clipboard/browser/clipboard'
import 'monaco-editor/editor/contrib/comment/browser/comment'
import 'monaco-editor/editor/contrib/contextmenu/browser/contextmenu'
import 'monaco-editor/editor/contrib/find/browser/findController'
import 'monaco-editor/editor/contrib/folding/browser/folding'
import 'monaco-editor/editor/contrib/indentation/browser/indentation'
import 'monaco-editor/editor/contrib/linesOperations/browser/linesOperations'
import 'monaco-editor/editor/contrib/multicursor/browser/multicursor'
import 'monaco-editor/editor/contrib/wordOperations/browser/wordOperations'

// registar za cpp registruje i "c" i "cpp"
import 'monaco-editor/languages/definitions/cpp/register'
import 'monaco-editor/languages/definitions/java/register'

// CSS uvozimo relativnom putanjom, jer "exports" mapa paketa za bare uvoze dodaje ".js" na kraj
import '../node_modules/monaco-editor/esm/vs/base/browser/ui/codicons/codicon/codicon.css'
import '../node_modules/monaco-editor/esm/vs/base/browser/ui/codicons/codicon/codicon-modifiers.css'
// Monaco pokreće deo posla u web worker-u; Vite ga ovako pakuje
const globalScope = self as unknown as { MonacoEnvironment: monaco.Environment }
globalScope.MonacoEnvironment = {
  getWorker: () => new editorWorker(),
}

interface RunResult {
  verdict: string
  exitCode: number
  stdout: string
  stderr: string
  timeMs: number
}

interface Language {
  label: string
  monaco: string
  template: string
}

const LANGUAGES: Record<string, Language> = {
  cpp: {
    label: 'C++',
    monaco: 'cpp',
    template: [
      '#include <iostream>',
      '',
      'int main() {',
      '    std::cout << "Hello, world!" << std::endl;',
      '}',
      '',
    ].join('\n'),
  },
  c: {
    label: 'C',
    monaco: 'c',
    template: [
      '#include <stdio.h>',
      '',
      'int main() {',
      '    printf("Hello, world!\\n");',
      '    return 0;',
      '}',
      '',
    ].join('\n'),
  },
  java: {
    label: 'Java',
    monaco: 'java',
    template: [
      'public class Main {',
      '    public static void main(String[] args) {',
      '        System.out.println("Hello, world!");',
      '    }',
      '}',
      '',
    ].join('\n'),
  },
}

const app = document.querySelector<HTMLDivElement>('#app')!
app.innerHTML = `
  <header>
    <h1>code-runner</h1>
    <div class="controls">
      <select id="language"></select>
      <button id="run">Run (Ctrl+Enter)</button>
      <button id="share">Share</button>
      <span id="share-link"></span>
    </div>
  </header>
  <main>
    <section class="editor-pane"><div id="editor"></div></section>
    <section class="side-pane">
      <label for="stdin">Input (stdin)</label>
      <textarea id="stdin" spellcheck="false"></textarea>
      <div class="output-header">
        <span>Output</span>
        <span id="verdict" class="verdict"></span>
        <span id="time"></span>
      </div>
      <pre id="stdout"></pre>
      <pre id="stderr"></pre>
    </section>
  </main>
`

const languageSelect = document.querySelector<HTMLSelectElement>('#language')!
const runButton = document.querySelector<HTMLButtonElement>('#run')!
const stdinInput = document.querySelector<HTMLTextAreaElement>('#stdin')!
const verdictEl = document.querySelector<HTMLSpanElement>('#verdict')!
const timeEl = document.querySelector<HTMLSpanElement>('#time')!
const stdoutEl = document.querySelector<HTMLPreElement>('#stdout')!
const stderrEl = document.querySelector<HTMLPreElement>('#stderr')!
const shareButton = document.querySelector<HTMLButtonElement>('#share')!
const shareLinkEl = document.querySelector<HTMLSpanElement>('#share-link')!

for (const [id, lang] of Object.entries(LANGUAGES)) {
  const option = document.createElement('option')
  option.value = id
  option.textContent = lang.label
  languageSelect.appendChild(option)
}

let currentLanguage = 'cpp'
const codeByLanguage: Record<string, string> = {}

const editor = monaco.editor.create(document.querySelector<HTMLDivElement>('#editor')!, {
  value: LANGUAGES[currentLanguage].template,
  language: LANGUAGES[currentLanguage].monaco,
  theme: 'vs-dark',
  automaticLayout: true,
  minimap: { enabled: false },
  fontSize: 14,
})

// pri promeni jezika pamtimo kod koji je korisnik pisao za prethodni jezik
languageSelect.addEventListener('change', () => {
  codeByLanguage[currentLanguage] = editor.getValue()
  currentLanguage = languageSelect.value
  monaco.editor.setModelLanguage(editor.getModel()!, LANGUAGES[currentLanguage].monaco)
  editor.setValue(codeByLanguage[currentLanguage] ?? LANGUAGES[currentLanguage].template)
})

// izlaz programa je nepoverljiv tekst, zato uvek textContent, nikad innerHTML
function showResult(result: RunResult) {
  verdictEl.textContent = result.verdict
  verdictEl.className = `verdict verdict-${result.verdict}`
  timeEl.textContent = `${result.timeMs} ms`
  stdoutEl.textContent = result.stdout
  stderrEl.textContent = result.stderr
}

function showError(message: string) {
  verdictEl.textContent = 'ERROR'
  verdictEl.className = 'verdict verdict-ERROR'
  timeEl.textContent = ''
  stdoutEl.textContent = ''
  stderrEl.textContent = message
}

async function run() {
  runButton.disabled = true
  verdictEl.textContent = 'RUNNING'
  verdictEl.className = 'verdict verdict-RUNNING'
  timeEl.textContent = ''
  stdoutEl.textContent = ''
  stderrEl.textContent = ''

  try {
    const response = await fetch('/api/runs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        language: languageSelect.value,
        source: editor.getValue(),
        stdin: stdinInput.value,
      }),
    })

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      showError(body?.message ?? `HTTP ${response.status}`)
      return
    }

    showResult(await response.json())
  } catch {
    showError('Cannot reach the server')
  } finally {
    runButton.disabled = false
  }
}

runButton.addEventListener('click', () => void run())
editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => void run())

async function share() {
  shareButton.disabled = true
  shareLinkEl.textContent = 'Saving...'
  shareLinkEl.className = ''

  try {
    const response = await fetch('/api/snippets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        language: languageSelect.value,
        source: editor.getValue(),
        stdin: stdinInput.value,
      }),
    })

    if (!response.ok) {
      shareLinkEl.textContent = 'Failed to save'
      return
    }

    const { id } = await response.json()
    const url = `${location.origin}${location.pathname}?id=${id}`

    shareLinkEl.textContent = ''
    const link = document.createElement('a')
    link.href = url
    link.textContent = url
    shareLinkEl.appendChild(link)

    await navigator.clipboard.writeText(url).catch(() => {})
    shareLinkEl.classList.add('copied')
  } catch {
    shareLinkEl.textContent = 'Cannot reach the server'
  } finally {
    shareButton.disabled = false
  }
}

async function loadSharedSnippet() {
  const id = new URLSearchParams(location.search).get('id')
  if (!id) return

  try {
    const response = await fetch(`/api/snippets/${encodeURIComponent(id)}`)
    if (!response.ok) return

    const snippet = await response.json()
    if (!(snippet.language in LANGUAGES)) return

    codeByLanguage[currentLanguage] = editor.getValue()
    currentLanguage = snippet.language
    languageSelect.value = currentLanguage
    monaco.editor.setModelLanguage(editor.getModel()!, LANGUAGES[currentLanguage].monaco)
    editor.setValue(snippet.source)
    stdinInput.value = snippet.stdin
  } catch {
    // tiho odustani; korisnik i dalje ima prazan editor sa podrazumevanim šablonom
  }
}

shareButton.addEventListener('click', () => void share())
void loadSharedSnippet()