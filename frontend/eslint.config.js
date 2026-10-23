import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: [
      'dist',
      'coverage',
      'playwright-report',
      'test-results',
      'src/shared/api/schema.d.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        {
          allowConstantExport: true,
          allowExportNames: [
            'queryClient',
            'appRoutes',
            'useTheme',
            'useAuth',
            'safeRedirect',
            'badgeVariants',
            'buttonVariants',
            'statusLabel',
          ],
        },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    // 디자인 시스템 R-08, 글자 크기 스케일: 스케일 밖 간격, margin, 임의 글자 크기를 막는다.
    files: ['src/**/*.tsx'],
    ignores: ['src/**/*.test.tsx', 'src/features/design-preview/**'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector:
            'Literal[value=/(^|\\s)(m[tb]-\\d|text-\\[\\d+px\\]|gap-(0\\.5|1\\.5|5)(\\s|$)|p[xy]?-(1\\.5|2\\.5|3\\.5|5)(\\s|$))/]',
          message:
            '디자인 시스템 R-08: margin, 스케일 밖 간격(6, 10, 14, 20px), text-[Npx] 임의값은 쓰지 않습니다. gap과 4px 배수, text-label/text-xs를 사용하세요.',
        },
      ],
    },
  },
  prettier,
)
