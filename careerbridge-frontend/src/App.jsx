import { BrowserRouter, Route, Routes } from 'react-router-dom';
import HomePage from './pages/home/HomePage';
import RegisterPage from './pages/auth/RegisterPage';
import LoginPage from './pages/auth/LoginPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import SetPasswordPage from './pages/auth/SetPasswordPage';
import OnboardingPage from './pages/onboarding/OnboardingPage';
import RegisterInstitutionPage from './pages/institution/RegisterInstitutionPage';
import CollegeDashboardPage from './pages/institution/CollegeDashboardPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/set-password" element={<SetPasswordPage />} />
        <Route path="/onboarding" element={<OnboardingPage />} />
        <Route path="/register-institution" element={<RegisterInstitutionPage />} />
        <Route path="/college-dashboard" element={<CollegeDashboardPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
