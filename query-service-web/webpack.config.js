module.exports = (config) => {
  // Add monaco-editor rules at the beginning so they're matched first
  config.module.rules.unshift(
    {
      test: /\.css$/,
      include: /node_modules[\\/]monaco-editor/,
      use: ['style-loader', 'css-loader']
    },
    {
      test: /\.(ttf|woff|woff2|eot)$/,
      include: /node_modules[\\/]monaco-editor/,
      type: 'asset/resource'
    }
  );

  return config;
};
