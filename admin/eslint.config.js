import hooks from "eslint-plugin-react-hooks";

export default [{
  files: ["src/**/*.jsx"],
  languageOptions: { parserOptions: { ecmaVersion: "latest", sourceType: "module", ecmaFeatures: { jsx: true } } },
  plugins: { "react-hooks": hooks },
  rules: { "react-hooks/rules-of-hooks": "error" },
}];
