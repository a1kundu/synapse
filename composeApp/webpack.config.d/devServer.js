// Disable file watching on static directories to prevent spurious page reloads
// (e.g. when Gradle builds or IDE writes temp files in the project tree).
if (config.devServer && config.devServer.static) {
    config.devServer.static = config.devServer.static.map(function (entry) {
        if (typeof entry === 'string') {
            return { directory: entry, watch: false };
        }
        entry.watch = false;
        return entry;
    });
}
