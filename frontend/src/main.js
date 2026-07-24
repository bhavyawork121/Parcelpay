import './style.css'

// ──────────────────────────────
// State Management
// ──────────────────────────────
const state = {
    currentScreen: 'home',
    screenHistory: [],
    parcels: loadParcels(),
    settings: {
        useGemini: true,
    },
    review: {
        phone: '',
        cod: '',
        confidence: null,
        isProcessing: false,
    },
}

function loadParcels() {
    try {
        return JSON.parse(localStorage.getItem('parcelpay_parcels') || '[]')
    } catch {
        return []
    }
}

function saveParcels() {
    localStorage.setItem('parcelpay_parcels', JSON.stringify(state.parcels))
}

// ──────────────────────────────
// Router
// ──────────────────────────────
function navigate(screen) {
    const current = document.querySelector('.screen.active')
    if (current) {
        current.classList.remove('active')
        current.classList.add('slide-out-left')
    }
    state.screenHistory.push(state.currentScreen)
    state.currentScreen = screen
    renderScreen(screen)
}

function goBack() {
    const prev = state.screenHistory.pop() || 'home'
    const current = document.querySelector('.screen.active')
    if (current) {
        current.classList.remove('active')
        // Reverse animation for back
        current.style.transform = 'translateX(30px)'
        current.style.opacity = '0'
    }
    state.currentScreen = prev
    renderScreen(prev, true)
}

function navigateHome() {
    state.screenHistory = []
    state.currentScreen = 'home'
    const app = document.getElementById('app')
    app.innerHTML = ''
    renderScreen('home')
}

function renderScreen(name, isBack = false) {
    // Remove old screens after transition
    setTimeout(() => {
        document.querySelectorAll('.screen:not(.active)').forEach(el => el.remove())
    }, 350)

    const app = document.getElementById('app')
    const screenEl = document.createElement('div')
    screenEl.className = 'screen'
    screenEl.id = `screen-${name}`

    switch (name) {
        case 'home':
            screenEl.innerHTML = renderHome()
            break
        case 'capture':
            screenEl.innerHTML = renderCapture()
            break
        case 'review':
            screenEl.innerHTML = renderReview()
            break
        case 'history':
            screenEl.innerHTML = renderHistory()
            break
        case 'settings':
            screenEl.innerHTML = renderSettings()
            break
    }

    app.appendChild(screenEl)

    // Trigger transition
    if (isBack) {
        screenEl.style.transform = 'translateX(-30px)'
    }

    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            screenEl.classList.add('active')
            screenEl.style.transform = ''
            screenEl.style.opacity = ''
        })
    })

    // Bind events after DOM is ready
    requestAnimationFrame(() => {
        switch (name) {
            case 'home': bindHomeEvents(); break
            case 'capture': bindCaptureEvents(); break
            case 'review': bindReviewEvents(); break
            case 'history': bindHistoryEvents(); break
            case 'settings': bindSettingsEvents(); break
        }
    })
}

// ──────────────────────────────
// HOME SCREEN
// ──────────────────────────────
function getStats() {
    const now = Date.now()
    const dayAgo = now - 24 * 60 * 60 * 1000
    const todayParcels = state.parcels.filter(p => p.timestamp > dayAgo)
    const totalCod = state.parcels.reduce((sum, p) => sum + (parseFloat(p.codAmount) || 0), 0)
    return {
        today: todayParcels.length,
        total: state.parcels.length,
        totalCod,
    }
}

function renderHome() {
    const stats = getStats()
    return `
    <div class="home-screen animate-in">
        <div class="home-header">
            <div>
                <div class="title">ParcelPay</div>
                <div class="subtitle">Smart COD Scanner</div>
            </div>
            <button class="settings-btn" id="btnSettings" aria-label="Settings">
                <span class="material-symbols-outlined">settings</span>
            </button>
        </div>

        <div class="stats-row">
            <div class="stat-card blue">
                <span class="material-symbols-outlined stat-icon">today</span>
                <div class="stat-value">${stats.today}</div>
                <div class="stat-label">Today</div>
            </div>
            <div class="stat-card green">
                <span class="material-symbols-outlined stat-icon">inventory_2</span>
                <div class="stat-value">${stats.total}</div>
                <div class="stat-label">Total Parcels</div>
            </div>
        </div>

        <div class="cod-card">
            <div>
                <div class="cod-label">Total COD</div>
                <div class="cod-value">₹${stats.totalCod.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</div>
            </div>
            <div class="cod-icon-circle">
                <span class="material-symbols-outlined">currency_rupee</span>
            </div>
        </div>

        <button class="scan-btn" id="btnScan">
            <span class="material-symbols-outlined">photo_camera</span>
            <div class="scan-text">
                <h3>Scan Parcel</h3>
                <p>Take a photo to extract details</p>
            </div>
        </button>

        <div class="quick-actions">
            <button class="quick-action-card" id="btnHistory">
                <span class="material-symbols-outlined">history</span>
                <span class="qa-label">History</span>
                <span class="material-symbols-outlined arrow-icon">arrow_forward</span>
            </button>
            <button class="quick-action-card" id="btnAnalytics">
                <span class="material-symbols-outlined">analytics</span>
                <span class="qa-label">Analytics</span>
                <span class="material-symbols-outlined arrow-icon">arrow_forward</span>
            </button>
        </div>
    </div>
    `
}

function bindHomeEvents() {
    document.getElementById('btnSettings')?.addEventListener('click', () => navigate('settings'))
    document.getElementById('btnScan')?.addEventListener('click', () => navigate('capture'))
    document.getElementById('btnHistory')?.addEventListener('click', () => navigate('history'))
    document.getElementById('btnAnalytics')?.addEventListener('click', () => showToast('Coming soon!', 'info'))
}

// ──────────────────────────────
// CAPTURE SCREEN
// ──────────────────────────────
function renderCapture() {
    return `
    <div class="top-bar">
        <button class="back-btn" id="captureBack">
            <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1>Scan Parcel</h1>
    </div>
    <div class="capture-content animate-in">
        <div class="camera-frame">
            <div class="camera-icon-circle">
                <span class="material-symbols-outlined">photo_camera</span>
            </div>
            <p>Position the parcel label<br>in the frame</p>
        </div>
        <div class="capture-actions">
            <button class="gallery-btn" id="btnGallery">
                <span class="material-symbols-outlined">photo_library</span>
                Gallery
            </button>
            <button class="camera-btn" id="btnCamera">
                <span class="material-symbols-outlined">photo_camera</span>
                Camera
            </button>
        </div>
    </div>
    `
}

function bindCaptureEvents() {
    document.getElementById('captureBack')?.addEventListener('click', goBack)

    // Both gallery and camera simulate going to review with mock OCR
    const goToReview = () => {
        // Simulate processing
        state.review = {
            phone: generateMockPhone(),
            cod: generateMockCod(),
            confidence: (0.78 + Math.random() * 0.2).toFixed(2),
            isProcessing: true,
        }
        navigate('review')
        // Simulate OCR delay
        setTimeout(() => {
            state.review.isProcessing = false
            const reviewScreen = document.getElementById('screen-review')
            if (reviewScreen) {
                reviewScreen.innerHTML = renderReview()
                bindReviewEvents()
                reviewScreen.classList.add('active')
            }
        }, 1500)
    }

    document.getElementById('btnGallery')?.addEventListener('click', goToReview)
    document.getElementById('btnCamera')?.addEventListener('click', goToReview)
}

function generateMockPhone() {
    const prefixes = ['98', '97', '96', '95', '94', '93', '91', '90', '88', '87', '86', '85', '70', '72', '73']
    const prefix = prefixes[Math.floor(Math.random() * prefixes.length)]
    let rest = ''
    for (let i = 0; i < 8; i++) rest += Math.floor(Math.random() * 10)
    return prefix + rest
}

function generateMockCod() {
    const amounts = [199, 249, 349, 499, 599, 799, 999, 1299, 1499, 1999, 2499]
    return amounts[Math.floor(Math.random() * amounts.length)].toString()
}

// ──────────────────────────────
// REVIEW SCREEN
// ──────────────────────────────
function renderReview() {
    if (state.review.isProcessing) {
        return `
        <div class="top-bar">
            <button class="back-btn" id="reviewBack">
                <span class="material-symbols-outlined">arrow_back</span>
            </button>
            <h1>Review Details</h1>
        </div>
        <div class="processing-overlay">
            <div class="spinner"></div>
            <div class="processing-text">Analyzing parcel...</div>
        </div>
        `
    }

    const conf = parseFloat(state.review.confidence)
    const isHigh = conf > 0.8
    const confClass = isHigh ? 'high' : 'medium'

    return `
    <div class="top-bar">
        <button class="back-btn" id="reviewBack">
            <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1>Review Details</h1>
    </div>
    <div class="review-content animate-in">
        <div class="review-image-card">
            <div class="review-image-placeholder">
                <span class="material-symbols-outlined">image</span>
                <span style="font-size: 13px;">Parcel label preview</span>
            </div>
        </div>

        ${state.review.confidence ? `
        <div class="confidence-card">
            <div class="confidence-icon ${confClass}">
                <span class="material-symbols-outlined">auto_awesome</span>
            </div>
            <div>
                <div class="confidence-label">OCR Confidence</div>
                <div class="confidence-value">${Math.round(conf * 100)}%</div>
            </div>
        </div>
        ` : ''}

        <div class="review-field">
            <div class="field-label">
                <span class="material-symbols-outlined">phone</span>
                Phone Number
            </div>
            <input type="tel" id="reviewPhone" value="${state.review.phone}" placeholder="Enter phone number" maxlength="10">
        </div>

        <div class="review-field">
            <div class="field-label">
                <span class="material-symbols-outlined">currency_rupee</span>
                COD Amount (₹)
            </div>
            <input type="number" id="reviewCod" value="${state.review.cod}" placeholder="Enter COD amount">
        </div>

        <button class="save-btn" id="btnSave">
            <span class="material-symbols-outlined">check</span>
            Save Parcel
        </button>
    </div>
    `
}

function bindReviewEvents() {
    document.getElementById('reviewBack')?.addEventListener('click', goBack)

    document.getElementById('reviewPhone')?.addEventListener('input', (e) => {
        state.review.phone = e.target.value
    })

    document.getElementById('reviewCod')?.addEventListener('input', (e) => {
        state.review.cod = e.target.value
    })

    document.getElementById('btnSave')?.addEventListener('click', () => {
        const phone = state.review.phone.trim()
        const cod = state.review.cod.trim()

        if (!phone) {
            showToast('Enter a phone number', 'error')
            return
        }

        // Save parcel
        state.parcels.unshift({
            id: Date.now(),
            phoneNumber: phone,
            codAmount: cod || '0',
            timestamp: Date.now(),
        })
        saveParcels()

        showToast('Parcel saved!', 'success')

        // Navigate back to home
        setTimeout(() => navigateHome(), 600)
    })
}

// ──────────────────────────────
// HISTORY SCREEN
// ──────────────────────────────
function renderHistory() {
    if (state.parcels.length === 0) {
        return `
        <div class="top-bar">
            <button class="back-btn" id="historyBack">
                <span class="material-symbols-outlined">arrow_back</span>
            </button>
            <h1>History</h1>
        </div>
        <div class="empty-state">
            <span class="material-symbols-outlined">inventory_2</span>
            <div class="empty-title">No parcels yet</div>
            <div class="empty-subtitle">Start scanning to see history here</div>
        </div>
        `
    }

    const cards = state.parcels.map(p => {
        const timeStr = formatRelativeTime(p.timestamp)
        return `
        <div class="history-card">
            <div class="history-card-icon">
                <span class="material-symbols-outlined">local_shipping</span>
            </div>
            <div class="history-card-info">
                <div class="history-card-phone">${p.phoneNumber}</div>
                <div class="history-card-time">${timeStr}</div>
            </div>
            <div class="history-card-amount">₹${p.codAmount}</div>
        </div>
        `
    }).join('')

    return `
    <div class="top-bar">
        <button class="back-btn" id="historyBack">
            <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1>History</h1>
    </div>
    <div class="history-content animate-in">
        ${cards}
    </div>
    `
}

function bindHistoryEvents() {
    document.getElementById('historyBack')?.addEventListener('click', goBack)
}

function formatRelativeTime(timestamp) {
    const diff = Date.now() - timestamp
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (seconds < 60) return 'Just now'
    if (minutes < 60) return `${minutes}m ago`
    if (hours < 24) return `${hours}h ago`
    if (days < 7) return `${days}d ago`

    const d = new Date(timestamp)
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

// ──────────────────────────────
// SETTINGS SCREEN
// ──────────────────────────────
function renderSettings() {
    return `
    <div class="top-bar">
        <button class="back-btn" id="settingsBack">
            <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1>Settings</h1>
    </div>
    <div class="settings-content animate-in">
        <div class="settings-section-label">OCR Engine</div>

        <div class="setting-card">
            <div class="setting-icon-circle">
                <span class="material-symbols-outlined">auto_awesome</span>
            </div>
            <div class="setting-info">
                <div class="setting-title">Use Gemini AI</div>
                <div class="setting-subtitle">Cloud-based OCR for better accuracy</div>
            </div>
            <label class="toggle">
                <input type="checkbox" id="toggleGemini" ${state.settings.useGemini ? 'checked' : ''}>
                <span class="toggle-slider"></span>
            </label>
        </div>

        <div class="setting-card" id="onDeviceCard" style="display: ${state.settings.useGemini ? 'none' : 'flex'}">
            <div class="setting-icon-circle">
                <span class="material-symbols-outlined">phone_android</span>
            </div>
            <div class="setting-info">
                <div class="setting-title">On-device CRNN</div>
                <div class="setting-subtitle">Fast, offline phone number detection</div>
            </div>
            <span class="active-badge">Active</span>
        </div>

        <div style="height: 8px"></div>
        <div class="settings-section-label">About</div>

        <div class="setting-card">
            <div class="setting-icon-circle">
                <span class="material-symbols-outlined">info</span>
            </div>
            <div class="setting-info">
                <div class="setting-title">Version</div>
                <div class="setting-subtitle">1.0.0</div>
            </div>
        </div>

        <div class="setting-card" id="clearDataCard" style="cursor: pointer;">
            <div class="setting-icon-circle" style="background: rgba(239, 68, 68, 0.15);">
                <span class="material-symbols-outlined" style="color: var(--red);">delete</span>
            </div>
            <div class="setting-info">
                <div class="setting-title" style="color: var(--red);">Clear All Data</div>
                <div class="setting-subtitle">Remove all saved parcels</div>
            </div>
        </div>
    </div>
    `
}

function bindSettingsEvents() {
    document.getElementById('settingsBack')?.addEventListener('click', goBack)

    document.getElementById('toggleGemini')?.addEventListener('change', (e) => {
        state.settings.useGemini = e.target.checked
        const onDeviceCard = document.getElementById('onDeviceCard')
        if (onDeviceCard) {
            onDeviceCard.style.display = e.target.checked ? 'none' : 'flex'
        }
    })

    document.getElementById('clearDataCard')?.addEventListener('click', () => {
        if (state.parcels.length === 0) {
            showToast('Nothing to clear', 'info')
            return
        }
        state.parcels = []
        saveParcels()
        showToast('All data cleared', 'success')
    })
}

// ──────────────────────────────
// TOAST
// ──────────────────────────────
function showToast(message, type = 'success') {
    // Remove existing toast
    document.querySelectorAll('.toast').forEach(t => t.remove())

    const app = document.getElementById('app')
    const toast = document.createElement('div')
    toast.className = 'toast'

    const iconMap = {
        success: 'check_circle',
        error: 'error',
        info: 'info',
    }

    if (type === 'error') {
        toast.style.background = 'rgba(239, 68, 68, 0.9)'
    } else if (type === 'info') {
        toast.style.background = 'rgba(59, 130, 246, 0.9)'
    }

    toast.innerHTML = `<span class="material-symbols-outlined" style="font-size:18px">${iconMap[type]}</span>${message}`
    app.appendChild(toast)

    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            toast.classList.add('show')
        })
    })

    setTimeout(() => {
        toast.classList.remove('show')
        setTimeout(() => toast.remove(), 400)
    }, 2000)
}

// ──────────────────────────────
// Boot
// ──────────────────────────────
renderScreen('home')
