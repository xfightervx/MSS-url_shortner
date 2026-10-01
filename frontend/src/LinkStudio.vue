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

<style src="./LinkStudio.css"></style>
