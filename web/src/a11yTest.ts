import axe from 'axe-core'

const runner = document.createElement('button')
runner.id = 'infinitechat-a11y-runner'
runner.type = 'button'
runner.textContent = 'Run accessibility audit'
runner.style.cssText = 'position:fixed;left:-10000px;top:0;width:1px;height:1px;overflow:hidden'
runner.addEventListener('click', async () => {
  runner.dataset.status = 'running'
  const result = await axe.run(document, {
    resultTypes: ['violations'],
    runOnly: {
      type: 'tag',
      values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'],
    },
  })
  runner.dataset.result = JSON.stringify(
    result.violations.map((item) => ({
      id: item.id,
      impact: item.impact,
      help: item.help,
      nodes: item.nodes.map((node) => ({
        target: node.target,
        html: node.html,
        failureSummary: node.failureSummary,
      })),
    })),
  )
  runner.dataset.status = 'done'
})
document.body.append(runner)
