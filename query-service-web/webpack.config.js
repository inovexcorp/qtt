module.exports = (config) => {
  // Add monaco-editor rules at the beginning so they're matched first
  config.module.rules.unshift(
    {
      test: /\.css$/,
      include: /node_modules[\\/]monaco-editor/,
      // esModule: false makes css-loader emit require() for url(...) so the codicon font goes through the
      // asset/resource rule below. With ES modules it emitted new URL(..., import.meta.url), which Angular's
      // webpack config resolves to the build machine's absolute file:// path.
      use: ['style-loader', { loader: 'css-loader', options: { esModule: false } }]
    },
    {
      test: /\.(ttf|woff|woff2|eot)$/,
      include: /node_modules[\\/]monaco-editor/,
      type: 'asset/resource'
    }
  );

  return config;
};
