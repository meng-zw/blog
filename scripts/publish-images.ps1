[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9A-Za-z][0-9A-Za-z._-]{0,127}$')]
    [string]$Version,
    [string]$Repository = 'crpi-p853hywlwywfu2no.cn-hangzhou.personal.cr.aliyuncs.com/mengzw/my-storehouse'
)

$ErrorActionPreference = 'Stop'
$registry = 'crpi-p853hywlwywfu2no.cn-hangzhou.personal.cr.aliyuncs.com'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required. Install Docker Desktop or Docker Engine first.'
}

docker login --username=nick3337076276 $registry
if ($LASTEXITCODE -ne 0) { throw 'Docker login failed.' }

docker build --tag "$Repository`:api-$Version" ./blog-backend
if ($LASTEXITCODE -ne 0) { throw 'API image build failed.' }
docker build --tag "$Repository`:web-$Version" ./blog-frontend
if ($LASTEXITCODE -ne 0) { throw 'Web image build failed.' }

foreach ($image in @("$Repository`:api-$Version", "$Repository`:web-$Version")) {
    docker push $image
    if ($LASTEXITCODE -ne 0) { throw "Image push failed: $image" }
}

if ($Version -ne 'latest') {
    foreach ($component in @('api', 'web')) {
        docker tag "$Repository`:$component-$Version" "$Repository`:$component-latest"
        if ($LASTEXITCODE -ne 0) { throw "Latest tag failed: $component" }
        docker push "$Repository`:$component-latest"
        if ($LASTEXITCODE -ne 0) { throw "Latest push failed: $component" }
    }
}
