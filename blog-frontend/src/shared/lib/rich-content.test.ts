import { describe, expect, it } from 'vitest'

import { prepareRichContent } from './rich-content'

describe('prepareRichContent', () => {
  it('preserves valid unique backend heading IDs', () => {
    const prepared = prepareRichContent('<h2 id="backend-intro">引言</h2><h3 id="backend-details">细节</h3>')

    expect(prepared.toc).toEqual([
      { id: 'backend-intro', text: '引言', level: 2 },
      { id: 'backend-details', text: '细节', level: 3 }
    ])
    expect(prepared.html).toContain('id="backend-intro"')
    expect(prepared.html).toContain('id="backend-details"')
  })

  it('generates collision-free fallbacks against every document ID and heading level', () => {
    const prepared = prepareRichContent([
      '<h1 id="foo">标题</h1>',
      '<div id="foo-2"></div>',
      '<h2>Foo</h2>',
      '<h3>Foo</h3>',
      '<h2 id="duplicate">甲</h2>',
      '<h3 id="duplicate">乙</h3>'
    ].join(''))

    expect(prepared.toc.map((item) => item.id)).toEqual(['foo-3', 'foo-4', '甲', '乙'])
    expect(new Set(prepared.toc.map((item) => item.id)).size).toBe(4)
    expect(prepared.html).toContain('id="foo-3"')
    expect(prepared.html).toContain('id="foo-4"')
  })
})
