<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Check,
  Copy,
  ExternalLink,
  Link2,
  LogOut,
  Plus,
  Search,
  ShieldCheck,
  Sparkles,
  Timer,
  X,
} from '@lucide/vue'

interface Account {
  name: string
  email: string
  passwordHash: string
}
interface ShortLink {
  code: string
  url: string
  shortUrl: string
  createdAt: string
  clicks: number
  localOnly: boolean
}
interface RedirectTarget {
  url: string
  localOnly: boolean
  ownerEmail: string
}

const route = useRoute()
const isRedirect = computed(() => route.name === 'redirect')
const authMode = ref<'signin' | 'signup'>('signin')
const currentUser = ref<Account | null>(null)
const accounts = ref<Account[]>([])
const links = ref<ShortLink[]>([])
const emailInput = ref('')
const nameInput = ref('')
const passwordInput = ref('')
const confirmPasswordInput = ref('')
const authError = ref('')
const linkError = ref('')
const linkNotice = ref('')
const searchQuery = ref('')
const targetInput = ref('')
const aliasInput = ref('')
const isCreating = ref(false)
const copiedCode = ref('')
const redirectTarget = ref('')
const redirectError = ref('')
const countdown = ref(10)
const isLoadingTarget = ref(false)
let countdownInterval: number | undefined

const apiRoot = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const apiOrigin = apiRoot || 'http://localhost:8080'
const filteredLinks = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  return query
    ? links.value.filter(
        (link) => link.url.toLowerCase().includes(query) || link.code.toLowerCase().includes(query),
      )
    : links.value
})
const totalClicks = computed(() => links.value.reduce((total, link) => total + link.clicks, 0))
const continueReady = computed(
  () => countdown.value === 0 && Boolean(redirectTarget.value) && !isLoadingTarget.value,
)

function readStorage<T>(key: string, fallback: T): T {
  try {
    const value = localStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : fallback
  } catch {
    return fallback
  }
}

function saveAccounts() {
  localStorage.setItem('mss-ai:accounts', JSON.stringify(accounts.value))
}

function saveLinks() {
  if (currentUser.value)
    localStorage.setItem(`mss-ai:links:${currentUser.value.email}`, JSON.stringify(links.value))
}

function saveRedirectTarget(link: ShortLink, ownerEmail: string) {
  const targets = readStorage<Record<string, RedirectTarget>>('mss-ai:targets', {})
  targets[link.code] = { url: link.url, localOnly: link.localOnly, ownerEmail }
  localStorage.setItem('mss-ai:targets', JSON.stringify(targets))
}

async function hashPassword(password: string) {
  const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(password))
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function signIn(account: Account) {
  currentUser.value = account
  links.value = readStorage<ShortLink[]>(`mss-ai:links:${account.email}`, [])
  localStorage.setItem('mss-ai:session', account.email)
}

function setAuthMode(mode: 'signin' | 'signup') {
  authMode.value = mode
  authError.value = ''
}

async function submitAuth() {
  authError.value = ''
  const email = emailInput.value.trim().toLowerCase()
  const password = passwordInput.value
  if (!email || !password) {
    authError.value = 'Enter your email and password to continue.'
    return
  }
  const passwordHash = await hashPassword(password)
  if (authMode.value === 'signup') {
    if (!nameInput.value.trim()) authError.value = 'Add your name to create an account.'
    else if (password.length < 8) authError.value = 'Use a password with at least 8 characters.'
    else if (password !== confirmPasswordInput.value)
      authError.value = 'Those passwords do not match.'
    else if (accounts.value.some((account) => account.email === email))
      authError.value = 'An account with that email already exists.'
    else {
      const account = { name: nameInput.value.trim(), email, passwordHash }
      accounts.value.push(account)
      saveAccounts()
      signIn(account)
    }
    return
  }
  const account = accounts.value.find((candidate) => candidate.email === email)
  if (!account || account.passwordHash !== passwordHash)
    authError.value = 'That email and password combination was not found.'
  else signIn(account)
}

function signOut() {
  localStorage.removeItem('mss-ai:session')
  currentUser.value = null
  links.value = []
  passwordInput.value = ''
  authMode.value = 'signin'
}

function shortAddress(code: string) {
  return `${window.location.origin}/redirect/${code}`
}

function makeLocalCode() {
  const alphabet = '23456789abcdefghjkmnpqrstuvwxyz'
  const targets = readStorage<Record<string, RedirectTarget>>('mss-ai:targets', {})
  let code = ''
  do {
    const randomValues = window.crypto.getRandomValues(new Uint32Array(7))
    code = Array.from(randomValues, (value) => alphabet[value % alphabet.length]).join('')
  } while (targets[code])
  return code
}

async function createLink() {
  linkError.value = ''
  linkNotice.value = ''
  let parsedUrl: URL
  try {
    parsedUrl = new URL(targetInput.value.trim())
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      throw new Error('Destination must use http or https.')
    }
  } catch {
    linkError.value = 'Enter a valid http or https destination.'
    return
  }
  const alias = aliasInput.value.trim()
  if (alias && !/^[A-Za-z0-9_-]{3,32}$/.test(alias)) {
    linkError.value = 'Aliases need 3–32 letters, numbers, dashes, or underscores.'
    return
  }
  isCreating.value = true
  let link: ShortLink
  try {
    const response = await fetch(`${apiRoot}/api/links`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: parsedUrl.href, alias: alias || null }),
    })
    if (!response.ok) {
      if (response.status >= 500) throw new TypeError('The link API is unavailable.')
      const body = (await response.json().catch(() => ({}))) as { message?: string }
      throw new Error(body.message || 'The link could not be created.')
    }
    const result = (await response.json()) as { code: string }
    link = {
      code: result.code,
      url: parsedUrl.href,
      shortUrl: shortAddress(result.code),
      createdAt: new Date().toISOString(),
      clicks: 0,
      localOnly: false,
    }
  } catch (error) {
    if (!(error instanceof TypeError)) {
      linkError.value = error instanceof Error ? error.message : 'The link could not be created.'
      isCreating.value = false
      return
    }
    const code = alias || makeLocalCode()
    if (links.value.some((existing) => existing.code === code)) {
      linkError.value = 'That alias is already in your links.'
      isCreating.value = false
      return
    }
    link = {
      code,
      url: parsedUrl.href,
      shortUrl: shortAddress(code),
      createdAt: new Date().toISOString(),
      clicks: 0,
      localOnly: true,
    }
    linkNotice.value = 'Saved in this browser. Connect the API to publish this link for everyone.'
  }
  links.value = [link, ...links.value]
  saveLinks()
  saveRedirectTarget(link, currentUser.value!.email)
  targetInput.value = ''
  aliasInput.value = ''
  isCreating.value = false
}

async function copyLink(link: ShortLink) {
  try {
    await navigator.clipboard.writeText(link.shortUrl)
    copiedCode.value = link.code
    window.setTimeout(() => (copiedCode.value = ''), 1800)
  } catch {
    linkError.value = 'Clipboard access is unavailable in this browser.'
  }
}

function incrementClickCount(code: string) {
  const targets = readStorage<Record<string, RedirectTarget>>('mss-ai:targets', {})
  const ownerEmail = targets[code]?.ownerEmail
  if (!ownerEmail) return
  const ownerLinks = readStorage<ShortLink[]>(`mss-ai:links:${ownerEmail}`, [])
  const link = ownerLinks.find((candidate) => candidate.code === code)
  if (!link) return
  link.clicks += 1
  localStorage.setItem(`mss-ai:links:${ownerEmail}`, JSON.stringify(ownerLinks))
  if (currentUser.value?.email === ownerEmail) links.value = ownerLinks
}

async function loadRedirectTarget() {
  const code = String(route.params.shortUrl ?? '')
  const targets = readStorage<Record<string, RedirectTarget>>('mss-ai:targets', {})
  if (targets[code]) {
    redirectTarget.value = targets[code].url
    return
  }
  isLoadingTarget.value = true
  try {
    const response = await fetch(`${apiRoot}/api/links/${encodeURIComponent(code)}`)
    if (!response.ok) throw new Error('The short link could not be found.')
    const link = (await response.json()) as { code: string; url: string }
    redirectTarget.value = link.url
    saveRedirectTarget(
      {
        code: link.code,
        url: link.url,
        shortUrl: shortAddress(link.code),
        createdAt: new Date().toISOString(),
        clicks: 0,
        localOnly: false,
      },
      '',
    )
  } catch {
    redirectError.value = 'This short link could not be found.'
  } finally {
    isLoadingTarget.value = false
  }
}

function continueToTarget() {
  if (!continueReady.value) return
  const code = String(route.params.shortUrl ?? '')
  const target = readStorage<Record<string, RedirectTarget>>('mss-ai:targets', {})[code]
  if (target?.ownerEmail) incrementClickCount(code)
  window.location.assign(
    target?.localOnly ? redirectTarget.value : `${apiOrigin}/redirect/${encodeURIComponent(code)}`,
  )
}

function formatDate(isoDate: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(isoDate))
}

onMounted(() => {
  accounts.value = readStorage<Account[]>('mss-ai:accounts', [])
  if (isRedirect.value) {
    void loadRedirectTarget()
    countdownInterval = window.setInterval(() => {
      countdown.value = Math.max(0, countdown.value - 1)
      if (countdown.value === 0 && countdownInterval) window.clearInterval(countdownInterval)
    }, 1000)
    return
  }
  const savedEmail = localStorage.getItem('mss-ai:session')
  const account = accounts.value.find((candidate) => candidate.email === savedEmail)
  if (account) signIn(account)
})

onBeforeUnmount(() => {
  if (countdownInterval) window.clearInterval(countdownInterval)
})
</script>

<template>
  <main v-if="isRedirect" class="redirect-page">
    <header class="redirect-header">
      <a class="wordmark" href="/" aria-label="MSS.AI home"><span>MSS</span><i>.</i>AI</a>
      <span class="secure-label"><ShieldCheck :size="15" /> Safe redirect</span>
    </header>
    <section class="redirect-content" aria-live="polite">
      <div class="timer-orbit" :class="{ ready: countdown === 0 }">
        <Timer v-if="countdown > 0" :size="30" :stroke-width="1.5" />
        <ArrowDownLeft v-else :size="30" :stroke-width="1.5" />
        <span>{{ countdown > 0 ? countdown : '✓' }}</span>
      </div>
      <p class="eyebrow">ONE MOMENT</p>
      <h1>{{ countdown ? 'Your link is on its way.' : 'Ready when you are.' }}</h1>
      <p class="redirect-copy">You’re heading to the destination below.</p>
      <div class="destination-row">
        <span class="destination-icon"><ExternalLink :size="18" /></span>
        <span v-if="isLoadingTarget" class="destination-url">Checking destination…</span>
        <span v-else class="destination-url">{{
          redirectTarget || 'Destination unavailable'
        }}</span>
      </div>
      <p v-if="redirectError" class="form-error">{{ redirectError }}</p>
      <button class="continue-button" :disabled="!continueReady" @click="continueToTarget">
        {{ countdown ? `Continue in ${countdown}s` : 'Continue to website' }}
        <ArrowRight :size="18" />
      </button>
      <p class="redirect-footnote">Please check the address before continuing.</p>
    </section>
    <div class="redirect-watermark" aria-hidden="true">MSS.AI</div>
  </main>

  <main v-else-if="!currentUser" class="auth-page">
    <section class="auth-story">
      <a class="wordmark wordmark-light" href="/" aria-label="MSS.AI home"
        ><span>MSS</span><i>.</i>AI</a
      >
      <div class="story-copy">
        <p class="eyebrow eyebrow-light">
          <Sparkles :size="14" /> A little less link, a lot more room
        </p>
        <h1>Make every<br />link <em>count.</em></h1>
        <p class="story-description">The small link tool for your big ideas.</p>
        <div class="story-link" aria-hidden="true">
          <div class="story-link-icon"><Link2 :size="20" /></div>
          <div><span>YOUR NEXT GREAT LINK</span><strong>mss.ai/launch-day</strong></div>
          <ArrowUpRight :size="18" class="story-arrow" />
        </div>
      </div>
      <div class="story-bottom"><span>LINKS, WITH INTENTION.</span><span>01 / 03</span></div>
    </section>
    <section class="auth-panel">
      <div class="auth-panel-top">
        <span>WELCOME TO MSS.AI</span><span class="online-mark"><i></i> YOUR LINK SPACE</span>
      </div>
      <div class="auth-form-wrap">
        <p class="eyebrow">YOUR LINKS, IN ONE PLACE</p>
        <h2>{{ authMode === 'signin' ? 'Good to see you.' : 'Let’s get started.' }}</h2>
        <p class="auth-intro">
          {{
            authMode === 'signin'
              ? 'Sign in to pick up where you left off.'
              : 'Create your space and make your first short link.'
          }}
        </p>
        <div class="auth-switch" role="tablist" aria-label="Account access">
          <button
            :class="{ selected: authMode === 'signin' }"
            role="tab"
            :aria-selected="authMode === 'signin'"
            @click="setAuthMode('signin')"
          >
            Sign in
          </button>
          <button
            :class="{ selected: authMode === 'signup' }"
            role="tab"
            :aria-selected="authMode === 'signup'"
            @click="setAuthMode('signup')"
          >
            Create account
          </button>
        </div>
        <form class="auth-form" @submit.prevent="submitAuth">
          <label v-if="authMode === 'signup'" class="field-label"
            >Your name
            <input v-model="nameInput" autocomplete="name" placeholder="Alex Morgan" required />
          </label>
          <label class="field-label"
            >Email address
            <input
              v-model="emailInput"
              type="email"
              autocomplete="email"
              placeholder="you@example.com"
              required
            />
          </label>
          <label class="field-label"
            >Password
            <input
              v-model="passwordInput"
              type="password"
              :autocomplete="authMode === 'signin' ? 'current-password' : 'new-password'"
              placeholder="At least 8 characters"
              required
            />
          </label>
          <label v-if="authMode === 'signup'" class="field-label"
            >Confirm password
            <input
              v-model="confirmPasswordInput"
              type="password"
              autocomplete="new-password"
              placeholder="Enter it again"
              required
            />
          </label>
          <p v-if="authError" class="form-error" role="alert">{{ authError }}</p>
          <button class="primary-button auth-submit" type="submit">
            {{ authMode === 'signin' ? 'Sign in to MSS.AI' : 'Create your account'
            }}<ArrowRight :size="17" />
          </button>
        </form>
        <p class="local-auth-note">
          <ShieldCheck :size="14" /> Demo accounts stay on this browser.
        </p>
      </div>
      <footer class="auth-footer">
        <span>© MSS.AI</span
        ><a href="mailto:hello@mss.ai">Need a hand? <ArrowUpRight :size="13" /></a>
      </footer>
    </section>
  </main>

  <main v-else class="dashboard-shell">
    <aside class="sidebar">
      <a class="wordmark dashboard-wordmark" href="/" aria-label="MSS.AI home"
        ><span>MSS</span><i>.</i>AI</a
      >
      <div class="workspace-label">YOUR WORKSPACE</div>
      <nav class="side-nav" aria-label="Main navigation">
        <a class="nav-item active" href="#overview"
          ><span class="nav-icon"><Link2 :size="17" /></span>Overview
          <span class="nav-count">{{ links.length }}</span></a
        >
        <a class="nav-item" href="#your-links"
          ><span class="nav-icon"><ArrowUpRight :size="17" /></span>Your links</a
        >
      </nav>
      <div class="sidebar-note">
        <div class="note-icon"><Sparkles :size="17" /></div>
        <p>Make a little space for what matters.</p>
        <span>MSS.AI / LINK STUDIO</span>
      </div>
      <div class="sidebar-profile">
        <div class="avatar">{{ currentUser.name.slice(0, 1).toUpperCase() }}</div>
        <div class="profile-copy">
          <strong>{{ currentUser.name }}</strong
          ><span>{{ currentUser.email }}</span>
        </div>
        <button
          class="icon-button signout-button"
          aria-label="Sign out"
          title="Sign out"
          @click="signOut"
        >
          <LogOut :size="17" />
        </button>
      </div>
    </aside>
    <section class="dashboard-main" id="overview">
      <header class="dashboard-topbar">
        <span>YOUR LINK STUDIO</span
        ><span class="topbar-right"
          ><i></i> ALL SYSTEMS READY <span class="topbar-divider"></span
          ><span>LINK STUDIO / 01</span></span
        >
      </header>
      <div class="dashboard-content">
        <div class="dashboard-heading">
          <div>
            <p class="eyebrow">YOUR LINK STUDIO</p>
            <h1>Good links, <em>good things.</em></h1>
            <p class="dashboard-intro">Everything you’ve shortened, ready to go.</p>
          </div>
          <div class="heading-stamp">
            <span>YOUR SPACE</span
            ><strong>{{ currentUser.name.slice(0, 1).toUpperCase() }}<i>.</i></strong>
          </div>
        </div>
        <section class="stats-row" aria-label="Link summary">
          <div class="stat-block">
            <span>YOUR LINKS</span><strong>{{ links.length.toString().padStart(2, '0') }}</strong
            ><small>IN YOUR STUDIO</small>
          </div>
          <div class="stat-block">
            <span>LINK CLICKS</span><strong>{{ totalClicks.toString().padStart(2, '0') }}</strong
            ><small>FROM THIS BROWSER</small>
          </div>
          <div class="stat-block stat-accent">
            <span>LINK HEALTH</span><strong><i></i> Looking good</strong
            ><small>ALL LINKS ARE READY</small>
          </div>
        </section>
        <section class="create-section" aria-labelledby="create-heading">
          <div class="section-heading">
            <div>
              <p class="eyebrow">START WITH A URL</p>
              <h2 id="create-heading">Make it memorable.</h2>
            </div>
            <span class="section-index">01 / CREATE</span>
          </div>
          <form class="create-form" @submit.prevent="createLink">
            <label class="field-label destination-field"
              >Destination URL
              <span class="input-with-icon"
                ><ExternalLink :size="17" /><input
                  v-model="targetInput"
                  type="url"
                  placeholder="Paste a long link here…"
                  required
              /></span>
            </label>
            <label class="field-label alias-field"
              >CUSTOM ALIAS <span class="optional-label">OPTIONAL</span>
              <span class="alias-input"
                ><span>mss.ai/</span
                ><input
                  v-model="aliasInput"
                  autocomplete="off"
                  maxlength="32"
                  placeholder="your-link"
              /></span>
            </label>
            <button class="primary-button create-button" type="submit" :disabled="isCreating">
              <Plus v-if="!isCreating" :size="18" /><span v-else class="button-loader"></span
              >{{ isCreating ? 'Creating' : 'Shorten link'
              }}<ArrowRight v-if="!isCreating" :size="17" />
            </button>
          </form>
          <p v-if="linkError" class="form-error create-feedback" role="alert">
            <X :size="15" />{{ linkError }}
          </p>
          <output v-else-if="linkNotice" class="form-notice create-feedback">
            <Check :size="15" />{{ linkNotice }}
          </output>
        </section>
        <section class="links-section" id="your-links" aria-labelledby="links-heading">
          <div class="links-heading-row">
            <div class="section-heading">
              <div>
                <p class="eyebrow">THE GOOD STUFF</p>
                <h2 id="links-heading">
                  Your links <span class="heading-count">{{ links.length }}</span>
                </h2>
              </div>
            </div>
            <label class="search-field"
              ><Search :size="16" /><input
                v-model="searchQuery"
                aria-label="Search your links"
                placeholder="Find a link"
              /><kbd>/</kbd></label
            >
          </div>
          <div v-if="filteredLinks.length" class="link-list">
            <article v-for="link in filteredLinks" :key="link.code" class="link-row">
              <div class="link-mark"><Link2 :size="17" /></div>
              <div class="link-details">
                <a class="short-link" :href="`/redirect/${link.code}`"
                  >{{ link.shortUrl.replace(/^https?:\/\//, '') }} <ArrowUpRight :size="13" /></a
                ><span class="long-link">{{ link.url }}</span>
              </div>
              <div class="link-date">{{ formatDate(link.createdAt) }}</div>
              <div class="link-clicks">
                <strong>{{ link.clicks }}</strong
                ><span>clicks</span>
              </div>
              <span v-if="link.localOnly" class="local-pill">LOCAL</span>
              <button
                class="icon-button copy-button"
                :aria-label="copiedCode === link.code ? 'Copied' : 'Copy short link'"
                :title="copiedCode === link.code ? 'Copied' : 'Copy short link'"
                @click="copyLink(link)"
              >
                <Check v-if="copiedCode === link.code" :size="17" /><Copy v-else :size="17" />
              </button>
            </article>
          </div>
          <div v-else-if="searchQuery" class="empty-state">
            <Search :size="20" />
            <p>No links match “{{ searchQuery }}”.</p>
          </div>
          <div v-else class="empty-state">
            <div class="empty-mark"><Link2 :size="22" /></div>
            <p>Your next great link starts above.</p>
            <span>SHORTEN A URL TO SEE IT HERE</span>
          </div>
          <footer class="links-footer">
            <span>SHOWING {{ filteredLinks.length }} OF {{ links.length }} LINKS</span
            ><span>MADE TO GO FURTHER <ArrowUpRight :size="13" /></span>
          </footer>
        </section>
      </div>
      <footer class="dashboard-footer">
        <span>MSS.AI <i>·</i> SMALL LINKS, BIG PLANS.</span
        ><a href="mailto:hello@mss.ai">SUPPORT <ArrowUpRight :size="12" /></a>
      </footer>
    </section>
  </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
:root {
  font-family: 'DM Sans', sans-serif;
  color: #1d2925;
  background: #f5f6f1;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-optical-sizing: auto;
  font-weight: 400;
  --ink: #1d2925;
  --muted: #78817b;
  --line: #e5e8e1;
  --paper: #fbfcf8;
  --green: #275e48;
  --lime: #c7f276;
  --orange: #e66e4f;
}
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
}
button,
input {
  font: inherit;
}
button,
a {
  -webkit-tap-highlight-color: transparent;
}
button {
  color: inherit;
}
a {
  color: inherit;
  text-decoration: none;
}
::selection {
  background: #c7f276;
  color: #1d2925;
}
.wordmark {
  display: inline-flex;
  align-items: baseline;
  color: var(--ink);
  font-family: 'Space Grotesk', sans-serif;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
}
.wordmark span {
  letter-spacing: -1.4px;
}
.wordmark i {
  color: var(--orange);
  font-style: normal;
}
.auth-page {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(430px, 0.92fr);
  min-height: 100vh;
  background: var(--paper);
  animation: appear 0.65s ease both;
}
.auth-story {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  min-height: 100vh;
  padding: 43px clamp(34px, 6.2vw, 96px) 30px;
  color: #f6f7f0;
  background: #205b47;
}
.auth-story:before,
.auth-story:after {
  position: absolute;
  right: -158px;
  bottom: -259px;
  width: 590px;
  height: 590px;
  border: 1px solid rgba(212, 242, 191, 0.22);
  border-radius: 50%;
  content: '';
}
.auth-story:after {
  right: -82px;
  bottom: -180px;
  width: 435px;
  height: 435px;
  border-color: rgba(212, 242, 191, 0.18);
}
.wordmark-light {
  z-index: 1;
  width: fit-content;
  color: #f7f8f3;
}
.story-copy {
  position: relative;
  z-index: 1;
  width: min(100%, 530px);
  margin: 0 auto;
  padding: 28px 0 45px;
}
.eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #819087;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.5;
}
.eyebrow-light {
  color: #c7e1d2;
}
.story-copy h1 {
  margin: 25px 0 17px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: clamp(56px, 6.4vw, 91px);
  font-weight: 500;
  letter-spacing: 0;
  line-height: 0.99;
}
.story-copy h1 em {
  color: var(--lime);
  font-style: normal;
}
.story-description {
  margin: 0;
  color: rgba(246, 247, 240, 0.74);
  font-size: 16px;
}
.story-link {
  display: flex;
  align-items: center;
  gap: 14px;
  width: min(100%, 390px);
  margin-top: 56px;
  padding: 16px 17px;
  border: 1px solid rgba(230, 244, 225, 0.2);
  background: rgba(14, 51, 39, 0.17);
}
.story-link-icon {
  display: grid;
  width: 39px;
  height: 39px;
  flex: 0 0 auto;
  place-items: center;
  color: var(--lime);
  background: rgba(199, 242, 118, 0.13);
}
.story-link div:nth-child(2) {
  display: grid;
  gap: 5px;
}
.story-link div:nth-child(2) span,
.story-bottom,
.auth-panel-top,
.auth-footer,
.workspace-label,
.sidebar-note span,
.dashboard-topbar,
.stat-block > span,
.stat-block small,
.section-index,
.alias-field,
.local-pill,
.empty-state span,
.links-footer,
.dashboard-footer {
  font-family: 'DM Mono', monospace;
  font-size: 9px;
  font-weight: 400;
  letter-spacing: 0;
}
.story-link div:nth-child(2) span {
  color: rgba(232, 243, 231, 0.56);
}
.story-link div:nth-child(2) strong {
  color: #f6f7f0;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 17px;
  font-weight: 500;
}
.story-arrow {
  margin-left: auto;
  color: rgba(241, 246, 233, 0.72);
}
.story-bottom {
  z-index: 1;
  display: flex;
  justify-content: space-between;
  color: rgba(239, 246, 236, 0.58);
}
.auth-panel {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 42px clamp(34px, 6.1vw, 90px) 25px;
}
.auth-panel-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #838b84;
}
.online-mark {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #62716a;
}
.online-mark i,
.topbar-right > i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7fae70;
}
.auth-form-wrap {
  width: min(100%, 410px);
  margin: auto;
  padding: 49px 0;
}
.auth-form-wrap h2 {
  margin: 12px 0 6px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 34px;
  font-weight: 500;
  letter-spacing: 0;
}
.auth-intro {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}
.auth-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  margin: 34px 0 25px;
  padding: 4px;
  background: #eef0ea;
}
.auth-switch button {
  min-height: 39px;
  border: 0;
  background: transparent;
  color: #737c75;
  font-size: 13px;
  cursor: pointer;
}
.auth-switch button.selected {
  background: var(--paper);
  color: var(--ink);
  box-shadow: 0 1px 4px rgba(28, 42, 34, 0.08);
}
.auth-form {
  display: grid;
  gap: 17px;
}
.field-label {
  display: grid;
  gap: 8px;
  color: #505b54;
  font-size: 12px;
  font-weight: 600;
}
.field-label input {
  width: 100%;
  height: 46px;
  padding: 0 13px;
  border: 1px solid #dfe3dc;
  border-radius: 2px;
  outline: none;
  background: #fff;
  color: var(--ink);
  font-size: 13px;
  font-weight: 400;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.field-label input::placeholder {
  color: #a0a7a1;
}
.field-label input:focus {
  border-color: #57826b;
  box-shadow: 0 0 0 3px rgba(39, 94, 72, 0.1);
}
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 0;
  border-radius: 2px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s,
    transform 0.2s;
}
.primary-button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #315f4b;
}
.primary-button:disabled {
  opacity: 0.65;
  cursor: wait;
}
.auth-submit {
  width: 100%;
  min-height: 49px;
  margin-top: 4px;
}
.auth-submit svg {
  margin-left: auto;
}
.form-error,
.form-notice {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: #bb4f3b;
  font-size: 12px;
  line-height: 1.5;
}
.form-notice {
  color: #316e4d;
}
.local-auth-note {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 21px 0 0;
  color: #828b84;
  font-size: 11px;
}
.auth-footer {
  display: flex;
  justify-content: space-between;
  color: #929a94;
}
.auth-footer a {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #59645c;
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
}
.dashboard-shell {
  display: grid;
  grid-template-columns: 244px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--paper);
  animation: appear 0.55s ease both;
}
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 34px 20px 18px;
  border-right: 1px solid var(--line);
  background: #f1f3ed;
}
.dashboard-wordmark {
  width: fit-content;
  margin: 2px 0 59px 9px;
}
.workspace-label {
  margin: 0 0 12px 9px;
  color: #969e97;
}
.side-nav {
  display: grid;
  gap: 3px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  padding: 0 10px;
  color: #68736c;
  font-size: 13px;
  transition:
    background 0.2s,
    color 0.2s;
}
.nav-item:hover,
.nav-item.active {
  background: #e4e9df;
  color: #285c46;
}
.nav-icon {
  display: flex;
  color: #728078;
}
.nav-item.active .nav-icon {
  color: #326a50;
}
.nav-count {
  display: grid;
  min-width: 23px;
  height: 22px;
  place-items: center;
  margin-left: auto;
  background: #f4f6f0;
  color: #647168;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
}
.sidebar-note {
  margin-top: auto;
  margin-bottom: 21px;
  padding: 15px 13px 14px;
  border: 1px solid #e2e6dd;
  background: #f6f7f3;
}
.note-icon {
  color: var(--orange);
}
.sidebar-note p {
  margin: 11px 0 13px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 14px;
  line-height: 1.45;
}
.sidebar-note span {
  color: #929a92;
}
.sidebar-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 15px;
  border-top: 1px solid #e0e4dc;
}
.avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  background: #dce9d8;
  color: #32664e;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 15px;
}
.profile-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.profile-copy strong {
  overflow: hidden;
  color: #334139;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-copy span {
  overflow: hidden;
  color: #89918a;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.icon-button {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  background: transparent;
  color: #7d8880;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s;
}
.icon-button:hover {
  background: #e8ece5;
  color: var(--ink);
}
.signout-button {
  margin-left: auto;
}
.dashboard-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
}
.dashboard-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 53px;
  padding: 0 clamp(26px, 4.2vw, 62px);
  border-bottom: 1px solid var(--line);
  color: #838d85;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 9px;
}
.topbar-divider {
  width: 1px;
  height: 12px;
  margin: 0 5px;
  background: #dce1d9;
}
.dashboard-content {
  width: min(100%, 1190px);
  margin: 0 auto;
  padding: 49px clamp(26px, 4.2vw, 62px) 32px;
}
.dashboard-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 31px;
}
.dashboard-heading h1 {
  margin: 10px 0 7px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: clamp(31px, 3vw, 43px);
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.13;
}
.dashboard-heading h1 em {
  color: #397252;
  font-style: normal;
}
.dashboard-intro {
  margin: 0;
  color: #77817a;
  font-size: 13px;
}
.heading-stamp {
  display: grid;
  justify-items: center;
  gap: 1px;
  width: 74px;
  height: 74px;
  align-content: center;
  border: 1px solid #dce2d8;
  color: #7b877f;
}
.heading-stamp span {
  font-family: 'DM Mono', monospace;
  font-size: 8px;
}
.heading-stamp strong {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 33px;
  font-weight: 500;
  line-height: 1;
}
.heading-stamp i {
  color: var(--orange);
  font-style: normal;
}
.stats-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.35fr;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.stat-block {
  display: grid;
  align-content: center;
  min-height: 96px;
  padding: 15px 20px;
  border-right: 1px solid var(--line);
}
.stat-block:first-child {
  padding-left: 0;
}
.stat-block:last-child {
  border-right: 0;
}
.stat-block > span,
.stat-block small {
  color: #89928b;
}
.stat-block strong {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 7px 0 3px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 25px;
  font-weight: 500;
  line-height: 1;
}
.stat-block small {
  color: #9ba39c;
  font-size: 8px;
}
.stat-accent strong {
  font-family: 'DM Sans', sans-serif;
  font-size: 15px;
  font-weight: 500;
}
.stat-accent strong i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #84b276;
}
.create-section {
  padding: 33px 0 35px;
  border-bottom: 1px solid var(--line);
}
.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}
.section-heading h2 {
  margin: 7px 0 0;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 0;
}
.section-index {
  padding-bottom: 3px;
  color: #99a199;
}
.create-form {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) minmax(180px, 0.48fr) 154px;
  align-items: end;
  gap: 13px;
  margin-top: 21px;
}
.destination-field {
  min-width: 0;
}
.input-with-icon,
.alias-input {
  display: flex;
  align-items: center;
  height: 46px;
  border: 1px solid #dfe3dc;
  background: #fff;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.input-with-icon {
  gap: 10px;
  padding-left: 12px;
  color: #87948c;
}
.input-with-icon:focus-within,
.alias-input:focus-within {
  border-color: #57826b;
  box-shadow: 0 0 0 3px rgba(39, 94, 72, 0.1);
}
.input-with-icon input,
.alias-input input {
  width: 100%;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
}
.input-with-icon input::placeholder,
.alias-input input::placeholder {
  color: #a0a7a1;
}
.alias-field {
  gap: 7px;
  color: #505b54;
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  font-weight: 600;
}
.optional-label {
  color: #a0a7a0;
  font-family: 'DM Mono', monospace;
  font-size: 8px;
}
.alias-input {
  gap: 5px;
  padding: 0 10px;
  color: #61826c;
  font-family: 'DM Mono', monospace;
  font-size: 11px;
}
.alias-input input {
  font-family: 'DM Sans', sans-serif;
}
.create-button {
  min-height: 46px;
  padding: 0 13px;
  white-space: nowrap;
}
.create-button svg:last-child {
  margin-left: auto;
}
.button-loader {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.create-feedback {
  margin-top: 12px;
}
.links-section {
  padding: 30px 0 7px;
}
.links-heading-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}
.heading-count {
  display: inline-grid;
  width: 23px;
  height: 23px;
  place-items: center;
  margin-left: 4px;
  vertical-align: 3px;
  background: #edf0e9;
  color: #4c5f51;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
}
.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 216px;
  height: 36px;
  padding: 0 9px;
  border-bottom: 1px solid #dfe3dc;
  color: #8c968e;
}
.search-field input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-size: 11px;
}
.search-field input::placeholder {
  color: #a0a7a1;
}
.search-field kbd {
  display: grid;
  width: 18px;
  height: 19px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #e1e5dd;
  color: #a1a9a2;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
}
.link-list {
  margin-top: 19px;
  border-top: 1px solid var(--line);
}
.link-row {
  display: grid;
  grid-template-columns: 37px minmax(120px, 1fr) 96px 65px 46px 35px;
  align-items: center;
  gap: 10px;
  min-height: 71px;
  border-bottom: 1px solid var(--line);
  animation: row-in 0.3s ease both;
}
.link-mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  background: #edf1e9;
  color: #32664d;
}
.link-details {
  display: grid;
  min-width: 0;
  gap: 5px;
}
.short-link {
  display: flex;
  align-items: center;
  gap: 4px;
  width: fit-content;
  color: #2e674b;
  font-size: 12px;
  font-weight: 600;
}
.short-link:hover {
  color: #d56f4d;
}
.long-link {
  overflow: hidden;
  color: #89928b;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-date {
  color: #7f8982;
  font-size: 10px;
}
.link-clicks {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #78827a;
  font-size: 9px;
}
.link-clicks strong {
  color: #35443a;
  font-family: 'DM Mono', monospace;
  font-size: 12px;
  font-weight: 500;
}
.local-pill {
  justify-self: start;
  padding: 4px 5px;
  background: #fff2e8;
  color: #93422f;
  font-size: 8px;
}
.copy-button {
  width: 32px;
  height: 32px;
}
.empty-state {
  display: grid;
  min-height: 180px;
  align-content: center;
  justify-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--line);
  color: #829087;
  text-align: center;
}
.empty-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  background: #edf1e9;
  color: #47775a;
}
.empty-state p {
  margin: 0;
  color: #4b5a50;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 15px;
}
.empty-state span {
  color: #9ba39c;
  font-size: 8px;
}
.links-footer {
  display: flex;
  justify-content: space-between;
  padding: 13px 0 4px;
  color: #9ba39c;
  font-size: 8px;
}
.links-footer span:last-child {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #78877c;
}
.dashboard-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 46px;
  margin-top: auto;
  padding: 0 clamp(26px, 4.2vw, 62px);
  border-top: 1px solid var(--line);
  color: #9ba39c;
  font-size: 8px;
}
.dashboard-footer i {
  color: var(--orange);
  font-style: normal;
}
.dashboard-footer a {
  display: flex;
  align-items: center;
  gap: 4px;
}
.redirect-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  background: #f4f6f0;
  animation: appear 0.5s ease both;
}
.redirect-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 77px;
  padding: 0 clamp(22px, 5vw, 72px);
  border-bottom: 1px solid #e3e7de;
}
.secure-label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #6d7a70;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
}
.secure-label svg {
  color: #528265;
}
.redirect-content {
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100% - 40px, 510px);
  margin: auto;
  padding: 65px 0 82px;
  text-align: center;
}
.timer-orbit {
  position: relative;
  display: grid;
  width: 94px;
  height: 94px;
  place-items: center;
  margin-bottom: 29px;
  border: 1px solid #d8e1d4;
  border-radius: 50%;
  background: radial-gradient(circle, #e5f0df 0 57%, transparent 58%);
  color: #397553;
}
.timer-orbit:before,
.timer-orbit:after {
  position: absolute;
  inset: -9px;
  border: 1px solid rgba(68, 123, 83, 0.13);
  border-radius: 50%;
  content: '';
}
.timer-orbit:after {
  inset: -18px;
  border-color: rgba(68, 123, 83, 0.08);
}
.timer-orbit span {
  position: absolute;
  top: 56px;
  display: grid;
  min-width: 27px;
  height: 25px;
  place-items: center;
  padding: 0 5px;
  border-radius: 14px;
  background: #275e48;
  color: #fff;
  font-family: 'DM Mono', monospace;
  font-size: 11px;
}
.timer-orbit.ready {
  background: radial-gradient(circle, #dff0d8 0 57%, transparent 58%);
}
.redirect-content .eyebrow {
  color: #65806d;
}
.redirect-content h1 {
  margin: 10px 0 8px;
  font-family: 'Space Grotesk', sans-serif;
  font-size: clamp(29px, 5vw, 39px);
  font-weight: 500;
  letter-spacing: 0;
}
.redirect-copy {
  margin: 0;
  color: #77827a;
  font-size: 13px;
}
.destination-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 60px;
  margin: 28px 0 18px;
  padding: 11px 14px;
  border: 1px solid #e0e5dc;
  background: #fafbf7;
  text-align: left;
}
.destination-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  background: #eaf0e6;
  color: #365d42;
}
.destination-url {
  overflow: hidden;
  color: #43544a;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.continue-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 50px;
  margin-top: 2px;
  padding: 0 17px;
  border: 0;
  border-radius: 2px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s,
    opacity 0.2s;
}
.continue-button:hover:not(:disabled) {
  background: #316148;
}
.continue-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.redirect-footnote {
  margin: 13px 0 0;
  color: #8d978f;
  font-size: 10px;
}
.redirect-watermark {
  position: absolute;
  right: -20px;
  bottom: -80px;
  color: rgba(46, 98, 68, 0.035);
  font-family: 'Space Grotesk', sans-serif;
  font-size: clamp(140px, 24vw, 330px);
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  pointer-events: none;
}
@keyframes appear {
  from {
    opacity: 0;
    transform: translateY(7px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: minmax(0, 0.95fr) minmax(390px, 1.05fr);
  }
  .auth-story {
    padding-right: 38px;
    padding-left: 38px;
  }
  .auth-panel {
    padding-right: 37px;
    padding-left: 37px;
  }
  .dashboard-shell {
    grid-template-columns: 205px minmax(0, 1fr);
  }
  .sidebar {
    padding-right: 14px;
    padding-left: 14px;
  }
  .dashboard-topbar,
  .dashboard-footer {
    padding-right: 25px;
    padding-left: 25px;
  }
  .dashboard-content {
    padding-right: 25px;
    padding-left: 25px;
  }
  .create-form {
    grid-template-columns: minmax(170px, 1fr) minmax(145px, 0.6fr);
  }
  .create-button {
    grid-column: 2;
    justify-self: end;
    width: 100%;
  }
  .alias-field {
    grid-column: 1;
    grid-row: 2;
  }
  .link-row {
    grid-template-columns: 34px minmax(100px, 1fr) 70px 37px 34px;
    gap: 8px;
  }
  .link-clicks {
    display: none;
  }
}
@media (max-width: 680px) {
  .auth-page {
    display: flex;
    flex-direction: column;
  }
  .auth-story {
    min-height: 304px;
    padding: 23px 24px 18px;
  }
  .auth-story:before {
    right: -246px;
    bottom: -340px;
  }
  .auth-story:after {
    right: -185px;
    bottom: -300px;
  }
  .wordmark {
    font-size: 24px;
  }
  .story-copy {
    width: 100%;
    padding: 22px 0 25px;
  }
  .story-copy h1 {
    margin: 12px 0 8px;
    font-size: 48px;
  }
  .story-description {
    font-size: 13px;
  }
  .story-link {
    display: none;
  }
  .story-bottom {
    font-size: 8px;
  }
  .auth-panel {
    min-height: calc(100vh - 304px);
    padding: 22px 24px 18px;
  }
  .auth-panel-top {
    font-size: 8px;
  }
  .auth-form-wrap {
    padding: 44px 0;
  }
  .auth-form-wrap h2 {
    font-size: 30px;
  }
  .auth-footer {
    margin-top: auto;
  }
  .dashboard-shell {
    display: block;
  }
  .sidebar {
    position: relative;
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: center;
    height: auto;
    padding: 16px 18px 12px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
  .dashboard-wordmark {
    margin: 0;
  }
  .workspace-label,
  .sidebar-note,
  .profile-copy,
  .signout-button {
    display: none;
  }
  .side-nav {
    grid-column: 1/-1;
    grid-row: 2;
    display: flex;
    gap: 5px;
    margin-top: 14px;
  }
  .nav-item {
    min-height: 36px;
    padding: 0 9px;
    font-size: 11px;
  }
  .sidebar-profile {
    grid-column: 2;
    grid-row: 1;
    padding: 0;
    border: 0;
  }
  .dashboard-topbar {
    min-height: 42px;
    padding: 0 18px;
    font-size: 8px;
  }
  .topbar-right {
    gap: 6px;
    font-size: 7px;
  }
  .topbar-divider,
  .topbar-right > span:last-child {
    display: none;
  }
  .dashboard-content {
    padding: 30px 19px 26px;
  }
  .dashboard-heading {
    padding-bottom: 23px;
  }
  .dashboard-heading h1 {
    max-width: 290px;
    font-size: 30px;
  }
  .dashboard-intro {
    max-width: 260px;
    font-size: 12px;
  }
  .heading-stamp {
    width: 56px;
    height: 56px;
    flex: 0 0 auto;
  }
  .heading-stamp strong {
    font-size: 26px;
  }
  .heading-stamp span {
    font-size: 7px;
  }
  .stat-block {
    min-height: 79px;
    padding: 11px 9px;
  }
  .stat-block:first-child {
    padding-left: 0;
  }
  .stat-block > span,
  .stat-block small {
    font-size: 7px;
  }
  .stat-block strong {
    font-size: 20px;
  }
  .stat-accent strong {
    gap: 5px;
    font-size: 10px;
  }
  .stat-accent strong i {
    width: 6px;
    height: 6px;
  }
  .create-section {
    padding: 27px 0 28px;
  }
  .section-heading h2 {
    font-size: 19px;
  }
  .section-index {
    font-size: 8px;
  }
  .create-form {
    grid-template-columns: 1fr;
    gap: 12px;
    margin-top: 17px;
  }
  .alias-field {
    grid-column: auto;
    grid-row: auto;
  }
  .create-button {
    grid-column: auto;
    justify-self: stretch;
  }
  .links-heading-row {
    align-items: flex-end;
    gap: 10px;
  }
  .search-field {
    width: 136px;
    height: 33px;
    gap: 5px;
    padding: 0 5px;
  }
  .search-field input {
    font-size: 10px;
  }
  .search-field kbd {
    display: none;
  }
  .link-row {
    grid-template-columns: 31px minmax(0, 1fr) 31px;
    gap: 8px;
    min-height: 67px;
  }
  .link-mark {
    width: 30px;
    height: 30px;
  }
  .link-details {
    grid-column: 2;
  }
  .link-date,
  .link-clicks,
  .local-pill {
    display: none;
  }
  .copy-button {
    grid-column: 3;
    grid-row: 1;
  }
  .long-link {
    max-width: 100%;
    font-size: 9px;
  }
  .short-link {
    font-size: 11px;
  }
  .links-footer {
    font-size: 7px;
  }
  .dashboard-footer {
    min-height: 42px;
    padding: 0 19px;
    font-size: 7px;
  }
}
@media (prefers-reduced-motion: reduce) {
  *,
  *:before,
  *:after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
