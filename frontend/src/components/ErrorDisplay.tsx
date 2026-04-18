import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

interface ErrorDisplayProps {
  title?: string;
  message?: string;
  showBackButton?: boolean;
}

/**
 * Error display component
 */
export const ErrorDisplay: React.FC<ErrorDisplayProps> = ({
  title = 'Something went wrong',
  message = 'An error occurred while loading the data.',
  showBackButton = true,
}) => {
  const navigate = useNavigate();

  return (
    <Result
      status="error"
      title={title}
      subTitle={message}
      extra={
        showBackButton && (
          <Button type="primary" onClick={() => navigate('/')}>
            Back Home
          </Button>
        )
      }
    />
  );
};
