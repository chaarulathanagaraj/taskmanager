import { Spin } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';

interface LoadingProps {
  tip?: string;
  size?: 'small' | 'default' | 'large';
  fullScreen?: boolean;
}

/**
 * Loading spinner component
 */
export const Loading: React.FC<LoadingProps> = ({
  tip = 'Loading...',
  size = 'large',
  fullScreen = false,
}) => {
  const spinner = (
    <Spin
      indicator={<LoadingOutlined style={{ fontSize: size === 'large' ? 48 : 24 }} spin />}
      tip={tip}
      size={size}
    />
  );

  if (fullScreen) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          width: '100%',
        }}
      >
        {spinner}
      </div>
    );
  }

  return (
    <div
      style={{
        textAlign: 'center',
        padding: '50px 0',
      }}
    >
      {spinner}
    </div>
  );
};
