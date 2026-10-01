import { afterEach, describe, expect, it, vi } from 'vitest'

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import LinkStudio from '../LinkStudio.vue'

describe('App', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  async function mountStudio(path = '/') {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: LinkStudio },
        { path: '/redirect/:shortUrl', name: 'redirect', component: LinkStudio },
      ],
    })
    await router.push(path)
    await router.isReady()
    return mount(App, { global: { plugins: [router] } })
  }

  it('renders the MSS.AI sign-in screen', async () => {
    const wrapper = await mountStudio()
    expect(wrapper.text()).toContain('MSS.AI')
    expect(wrapper.text()).toContain('Good to see you.')
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('keeps the redirect disabled until the ten-second wait ends', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ code: 'launch', url: 'https://example.com' }) }))
    const wrapper = await mountStudio('/redirect/launch')
    await flushPromises()
    const continueButton = wrapper.get('.continue-button')
    expect(continueButton.attributes('disabled')).toBeDefined()
    expect(continueButton.text()).toContain('Continue in 10s')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(continueButton.attributes('disabled')).toBeUndefined()
    expect(continueButton.text()).toContain('Continue to website')
  })
})
