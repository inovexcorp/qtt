module.exports = {
  module: {
    rules: [
      {
        test: /\.css$/,
        include: /node_modules\/monaco-editor/,
        use: [
          'style-loader',
          {
            loader: 'css-loader',
            options: {
              url: false // Don't resolve URLs in CSS files
            }
          }
        ]
      },
      {
        test: /\.(ttf|woff|woff2|eot)$/,
        include: /node_modules\/monaco-editor/,
        type: 'asset/resource'
      }
    ]
  }
};
