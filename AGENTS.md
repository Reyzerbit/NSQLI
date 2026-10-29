# Claude Agent Instructions

## Code Formatting

### Spacing

**Do not add extra spaces to align code vertically.**

This applies to all contexts: variable assignments, ternary expressions, method arguments, annotations, and any other constructs. Write each line with natural single-space separation only.

**Wrong:**
```java
double h    = height != null ? height.getValue() : 0.0;
double w    = width  != null ? width.getValue()  : 0.0;
double area = h      * w;

spec.setDisplayName("Title"  );
spec.setSection    ("Overview");
```

**Correct:**
```java
double h = height != null ? height.getValue() : 0.0;
double w = width != null ? width.getValue() : 0.0;
double area = h * w;

spec.setDisplayName("Title");
spec.setSection("Overview");
```

This rule applies everywhere: ternaries, assignments, annotations, chained calls, lambda bodies, and any other construct. Never pad with spaces to make columns line up.

### Brackets

For all brace-based languages (C#, Java, JavaScript, TypeScript, C++, etc.) **except for CSS/SCSS**, always use **Allman (newline) brace style**.

**Correct:**
```csharp
public void ExampleMethod(string value)
{
    if (value != null)
    {
        DoSomething();
    }
}
```

```javascript
function example(param)
{
    if (param)
    {
        doSomething();
    }
}
```

**Incorrect:**
```javascript
function example(param) {
    if (param) {
        doSomething();
    }
}
```

---

### SCSS Over CSS

- Always prefer **SCSS** over plain CSS for frontend development.
- Use SCSS nesting whenever appropriate.
- Every React component should have its own SCSS file whenever practical.
- Styling for a component should be wrapped inside a **single top-level class** corresponding to that component to prevent style leakage between components.

**Correct:**
```scss
.example-component
{
    width: 100px;

    .big-text
    {
        width: 20px;
    }
}
```

**Incorrect:**
```scss
.example-component
{
    width: 100px;
}

.big-text
{
    width: 20px;
}
```

Global variables, mixins, functions, resets, and shared utility styles should be placed in dedicated shared SCSS files such as:

- `_variables.scss`
- `_common.scss`
- `_mixins.scss`
- `_functions.scss`

Component-specific styles should not be placed in these shared files.

Always prefer style classes:
- `.example-element`

over element IDs:
- `#example-element`

---

### React Component Organization

- Prefer **one React component per file**.
- Avoid defining multiple React components within a single file.

---

### Frontend File Naming

All frontend files should use **kebab-case**.

**Correct:**

- `example-component.jsx`
- `user-profile.tsx`
- `use-auth.js`
- `theme-provider.tsx`
- `settings-modal.scss`

**Incorrect:**

- `ExampleComponent.jsx`
- `UserProfile.tsx`
- `ExampleHook.js`
- `ThemeProvider.tsx`

Never use PascalCase or camelCase for frontend filenames.