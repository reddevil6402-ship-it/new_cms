const fs = require('fs');

const md1 = fs.readFileSync('cms-architecture.md', 'utf8');
const md2 = fs.readFileSync('cms-diagrams.md', 'utf8');
const md3 = fs.readFileSync('db-structure.md', 'utf8');
const allText = md1 + ' ' + md2 + ' ' + md3 + ' Array API XMLHttpRequest ActiveXObject Animation Audio DOMContentLoaded';

const validWords = new Set();
const matches = allText.match(/[a-zA-Z_]+/g) || [];
matches.forEach(w => {
    if (w.includes('A')) {
        validWords.add(w);
    }
});

let html = fs.readFileSync('cms.html', 'utf8');

const sortedWords = Array.from(validWords).filter(w => w.length > 1).sort((a,b) => b.length - a.length);

let replacedCount = 0;
for (const word of sortedWords) {
    const corrupted = word.replace(/A/g, '-');
    if (corrupted === word) continue;
    
    // Skip words that are too short and risky to replace globally
    if (word === 'At' || word === 'As' || word === 'An' || word === 'Am' || word === 'Ah' || word === 'All' || word === 'Any') {
        // Special safe manual replacement for these if needed, but risky globally.
    } else {
        const escapeRegex = (str) => str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const regex = new RegExp('(?<![a-zA-Z])' + escapeRegex(corrupted) + '(?![a-zA-Z])', 'g');
        
        const count = (html.match(regex) || []).length;
        if (count > 0) {
            html = html.replace(regex, word);
            replacedCount += count;
            console.log('Restored: ' + word + ' (' + count + ' times)');
        }
    }
}

// Manual specific replacements for known HTML/CSS properties or safe words
html = html.replace(/(?<![a-zA-Z])-ll(?![a-zA-Z])/g, 'All');
html = html.replace(/(?<![a-zA-Z])-ny(?![a-zA-Z])/g, 'Any');

fs.writeFileSync('cms.html', html, 'utf8');
console.log('Total words restored: ' + replacedCount);
