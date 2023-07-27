const baseConfig = {
    branches: [],
    plugins: [
        '@semantic-release/commit-analyzer',
        '@semantic-release/release-notes-generator',
        '@semantic-release/changelog',
        "gradle-semantic-release-plugin",
        [
            '@semantic-release/git',
            {
                assets: ["gradle.properties"],
                message: 'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}\n\n\nskip-checks: true'
            }
        ],
    ],
    repositoryUrl: 'https://github.com/dyte-in/sockrates-client-kmm'
};

const mainConfig = {
    ...baseConfig,
    branches: ['main'],
}

const stagingConfig = {
    ...baseConfig,
    branches: ['main', { name: 'staging', prerelease: true }],
}

module.exports = process.env.ENVIRONMENT?.includes('staging') ? stagingConfig : mainConfig;
