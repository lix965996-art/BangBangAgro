const assetModules = import.meta.glob('../assets/*.{png,jpg,jpeg,gif,svg,webp}', {
  eager: true,
  query: '?url',
  import: 'default'
})

const assetUrls = Object.fromEntries(
  Object.entries(assetModules).map(([path, url]) => {
    const name = path.split('/').pop()
    return [name, url]
  })
)

export function assetUrl(name) {
  return assetUrls[name] || ''
}
