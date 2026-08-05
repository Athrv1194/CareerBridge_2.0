import { BrowserRouter, Route, Routes } from 'react-router-dom';
import HomePage from './pages/home/HomePage';
import RegisterPage from './pages/auth/RegisterPage';
import LoginPage from './pages/auth/LoginPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import OnboardingPage from './pages/onboarding/OnboardingPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/onboarding" element={<OnboardingPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
