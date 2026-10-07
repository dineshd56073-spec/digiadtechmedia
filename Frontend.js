// DigiAdTechMediaTN - Frontend Interactions & WhatsApp Lead Submission
document.addEventListener('DOMContentLoaded', () => {
  // Mobile Navigation Toggle
  const hamburger = document.getElementById('hamburgerBtn');
  const mobileNav = document.getElementById('mobileNav');
  const mobileLinks = mobileNav ? mobileNav.querySelectorAll('a') : [];

  if (hamburger && mobileNav) {
    hamburger.addEventListener('click', () => {
      mobileNav.classList.toggle('open');
      const isOpen = mobileNav.classList.contains('open');
      hamburger.setAttribute('aria-expanded', isOpen);
    });

    mobileLinks.forEach(link => {
      link.addEventListener('click', () => {
        mobileNav.classList.remove('open');
        hamburger.setAttribute('aria-expanded', 'false');
      });
    });
  }

  // Active Link Highlight on Scroll
  const sections = document.querySelectorAll('section[id]');
  const navLinks = document.querySelectorAll('.nav-links a');

  function updateActiveNavLink() {
    const scrollY = window.pageYOffset + 120;
    sections.forEach(section => {
      const sectionHeight = section.offsetHeight;
      const sectionTop = section.offsetTop;
      const sectionId = section.getAttribute('id');

      if (scrollY >= sectionTop && scrollY < sectionTop + sectionHeight) {
        navLinks.forEach(link => {
          link.classList.remove('active');
          if (link.getAttribute('href') === `#${sectionId}`) {
            link.classList.add('active');
          }
        });
      }
    });
  }

  window.addEventListener('scroll', updateActiveNavLink, { passive: true });

  // Contact Form Submission -> Direct WhatsApp Redirect
  const contactForm = document.getElementById('contactForm');
  const formToast = document.getElementById('formToast');
  const submitBtn = document.getElementById('submitBtn');

  if (contactForm) {
    contactForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const name = document.getElementById('formName').value.trim();
      const phone = document.getElementById('formPhone').value.trim();
      const email = document.getElementById('formEmail').value.trim();
      const service = document.getElementById('formService').value;
      const language = document.getElementById('formLanguage').value;
      const message = document.getElementById('formMessage').value.trim();

      if (!name || !phone) {
        showToast('Please provide your name and phone/WhatsApp number.', 'error');
        return;
      }

      const payload = {
        name,
        phone,
        email: email || 'Not provided',
        service,
        language,
        message: message || 'I would like more information about your courses/services.',
        timestamp: new Date().toISOString()
      };

      const originalBtnText = submitBtn.innerHTML;
      submitBtn.innerHTML = 'Connecting to WhatsApp...';
      submitBtn.disabled = true;

      // 1. Log lead asynchronously to backend if available (non-blocking)
      try {
        fetch('/api/contact', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }).catch(() => {});
      } catch (err) {
        // Backend offline / static site - ignore error
      }

      // 2. Format detailed message for WhatsApp (+91 96770 19690)
      const whatsappText =
        `Hello DigiAdTechMediaTN! 👋\n\n` +
        `*New Inquiry Details:*\n` +
        `👤 *Name:* ${payload.name}\n` +
        `📱 *Phone:* ${payload.phone}\n` +
        `📧 *Email:* ${payload.email}\n` +
        `🎯 *Interested Service:* ${payload.service}\n` +
        `🌐 *Preferred Language:* ${payload.language}\n` +
        `💬 *Message:* ${payload.message}`;

      const whatsappUrl = `https://wa.me/919677019690?text=${encodeURIComponent(whatsappText)}`;

      showToast('Inquiry received! Opening WhatsApp directly...', 'success');

      // 3. Directly open WhatsApp chat
      setTimeout(() => {
        window.open(whatsappUrl, '_blank');
        contactForm.reset();
        submitBtn.innerHTML = originalBtnText;
        submitBtn.disabled = false;
      }, 500);
    });
  }

  function showToast(message, type) {
    if (!formToast) return;
    formToast.className = `toast ${type}`;
    formToast.textContent = message;
    setTimeout(() => {
      formToast.className = 'toast';
    }, 6000);
  }
});