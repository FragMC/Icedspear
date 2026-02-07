import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/Icedspear/',
  title: "IcedSpear",
  description: "FragMC Core Plugin Documentation",
  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Core', link: '/core/' },
      { text: 'Addons', link: '/addons/weblink' },
      { text: 'API', link: '/api/' }
    ],

    sidebar: [
      {
        text: 'Core Plugin',
        items: [
      { text: 'Introduction', link: '/core/' },
      { text: 'Configuration', link: '/core/configuration' },
      { text: 'Mechanics', link: '/core/mechanics' },
      { text: 'Commands & Permissions', link: '/core/commands' },
      { text: 'Features', link: '/core/features' }
    ]
  },
  {
    text: 'WebLink',
    items: [
      { text: 'Overview', link: '/addons/weblink' },
      { text: 'Configuration', link: '/addons/weblink#configuration' },
      { text: 'Webhook API', link: '/addons/weblink#webhook-api' },
      { text: 'Database', link: '/addons/weblink#database-schema' }
    ]
  },
  {
    text: 'API',
    items: [
      { text: 'Getting Started', link: '/api/' }
    ]
  }
],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/stufy/iced-spear' }
    ],
    
    search: {
      provider: 'local'
    },
    
    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2024 FragMC'
    }
  }
})
