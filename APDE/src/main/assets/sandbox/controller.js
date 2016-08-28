var pJsInstance = null;

function updateCode(pdeCode) {
    var sketch = Processing.compile(pdeCode);
    
    if (pJsInstance) {
        pJsInstance.exit();
        pJsInstance = new Processing("canvas", sketch);
    } else {
        pJsInstance = new Processing("canvas", sketch);
    }
}

function kill() {
    if (pJsInstance != null) {
        pJsInstance.exit();
    }
}